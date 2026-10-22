package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.PageSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Luminance (0 black … 255 white) of a rendered page, row by row. */
class Luma(val width: Int, val height: Int, val values: ByteArray)

/**
 * A PDF opened with the framework [PdfRenderer]. Every call runs on one private thread, and each render
 * opens the page, draws it and closes it again, so memory stays flat even on long scanned books.
 */
class PdfDocumentHandle private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val thread: ExecutorCoroutineDispatcher,
    firstPage: PageSize,
) : AutoCloseable {

    val pageCount: Int = renderer.pageCount
    private val sizes = ConcurrentHashMap<Int, PageSize>().apply { put(0, firstPage) }
    private val fallback = firstPage

    fun pageSize(page: Int): PageSize = sizes[page] ?: fallback

    suspend fun measure(page: Int): PageSize = sizes[page] ?: withContext(thread) {
        renderer.openPage(page).use { PageSize(it.width.toFloat(), it.height.toFloat()) }.also { sizes[page] = it }
    }

    suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion? = null): Bitmap = withContext(thread) {
        renderer.openPage(page).use { pdfPage ->
            val size = PageSize(pdfPage.width.toFloat(), pdfPage.height.toFloat()).also { sizes[page] = it }
            val scale = widthPx / (crop.width * size.width)
            val bitmap = Bitmap.createBitmap(
                region?.width ?: widthPx,
                region?.height ?: renderedHeight(size, crop, widthPx),
                Bitmap.Config.ARGB_8888,
            ).apply { eraseColor(Color.WHITE) }
            val matrix = Matrix().apply {
                setTranslate(-crop.left * size.width, -crop.top * size.height)
                postScale(scale, scale)
                if (region != null) postTranslate(-region.x.toFloat(), -region.y.toFloat())
            }
            pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    /** Luminance of [crop] of [page] rendered [widthPx] wide (whole page by default). Never on the caller's thread. */
    suspend fun luminance(page: Int, widthPx: Int = 300, crop: CropBox = CropBox.FULL): Luma {
        val bitmap = render(page, crop, widthPx)
        // ~2.3 M pixels at the analysis width: keep the copy and loop off the UI thread (and off the PDF thread).
        return withContext(Dispatchers.Default) {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            val luma = ByteArray(pixels.size) { i ->
                val p = pixels[i]
                ((299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000).toByte()
            }
            Luma(widthPx, pixels.size / widthPx, luma)
        }
    }

    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) dev.kent.windbook.reader.closeReaderResources {
            try { runBlocking(thread) { renderer.close() } }
            finally { try { fd.close() } finally { thread.close() } }
        }
    }

    companion object {
        suspend fun open(file: File): PdfDocumentHandle =
            open(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))

        /** Consumes fd, including construction failure and cancellation before dispatcher delivery. */
        suspend fun open(fd: ParcelFileDescriptor): PdfDocumentHandle {
            val thread = Executors.newSingleThreadExecutor { Thread(it, "windbook-pdf") }.asCoroutineDispatcher()
            var opened: PdfDocumentHandle? = null
            try {
                return withContext(thread) {
                    val renderer = PdfRenderer(fd)
                    try {
                        if (renderer.pageCount == 0) throw java.io.IOException("PDF has no pages")
                        val first = renderer.openPage(0).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
                        PdfDocumentHandle(fd, renderer, thread, first).also { opened = it }
                    } catch (e: Throwable) { renderer.close(); throw e }
                }
            } catch (e: Throwable) {
                if (opened != null) opened.close()
                else { try { fd.close() } finally { thread.close() } }
                throw e
            }
        }
    }
}
