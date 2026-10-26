package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PageSize
import kotlin.math.roundToInt

/** A rectangle in the pixels of a page rendered at some width (used for zoomed tiles). */
data class PixelRegion(val x: Int, val y: Int, val width: Int, val height: Int)

/** Everything the PDF view needs; the app's tests fake it, production uses [PdfPages]. */
interface PdfPageSource {
    val textSelectionAvailable: Boolean get() = false
    suspend fun selectText(page: Int, start: dev.kent.windbook.core.model.PdfPoint, end: dev.kent.windbook.core.model.PdfPoint): dev.kent.windbook.core.model.PdfTextSelection? = null

    val pageCount: Int

    /** Best known size of [page] in points; pages not measured yet report the first page's size. */
    fun pageSize(page: Int): PageSize

    suspend fun measure(page: Int): PageSize

    /** Book-level crop (expensive: renders samples). Call once, off the main thread. */
    suspend fun cropPlan(): CropPlan

    /** The plan's box for [page] grown to this page's own content. */
    suspend fun pageCrop(page: Int, plan: CropPlan): CropBox

    suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion? = null): Bitmap
}

/** Height in pixels of [crop] of a page of [size] rendered [widthPx] wide. */
fun renderedHeight(size: PageSize, crop: CropBox, widthPx: Int): Int =
    (widthPx * (crop.height * size.height) / (crop.width * size.width)).roundToInt().coerceAtLeast(1)
