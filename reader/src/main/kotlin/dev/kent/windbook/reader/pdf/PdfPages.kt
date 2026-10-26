package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import android.util.LruCache
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.MarginDetector
import dev.kent.windbook.core.model.PageSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bitmap cache bound: min(48 MB, maxMemory / 6). */
fun defaultCacheBytes(): Int = minOf(48L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 6).toInt()

/** [PdfPageSource] over a [PdfDocumentHandle]: caches page bitmaps and computes crop boxes once. */
class PdfPages(
    private val document: PdfDocumentHandle,
    maxCacheBytes: Int = defaultCacheBytes(),
) : PdfPageSource {

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val planLock = Mutex()
    private var plan: CropPlan? = null
    private val boxLock = Mutex()
    private val ownBoxes = HashMap<Int, CropBox?>()

    override val textSelectionAvailable: Boolean get() = android.os.Build.VERSION.SDK_INT >= 35
    override suspend fun selectText(page: Int, start: dev.kent.windbook.core.model.PdfPoint, end: dev.kent.windbook.core.model.PdfPoint) = document.selectText(page,start,end)

    override val pageCount: Int get() = document.pageCount

    override fun pageSize(page: Int): PageSize = document.pageSize(page)

    override suspend fun measure(page: Int): PageSize = document.measure(page)

    override suspend fun cropPlan(): CropPlan = planLock.withLock {
        plan ?: run {
            val samples = MarginDetector.sampleIndices(pageCount)
            val boxes = samples.associateWith { ownBox(it) }
            CropPlan(
                odd = MarginDetector.bookBox(samples.filter { it % 2 == 0 }.map { boxes[it] }),
                even = MarginDetector.bookBox(samples.filter { it % 2 == 1 }.map { boxes[it] }),
            ).also { plan = it }
        }
    }

    override suspend fun pageCrop(page: Int, plan: CropPlan): CropBox =
        MarginDetector.forPage(plan.boxFor(page), ownBox(page))

    override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap {
        if (region != null) return document.render(page, crop, widthPx, region) // zoom tiles are not cached
        val key = "$page|$widthPx|$crop"
        cache.get(key)?.let { return it }
        return document.render(page, crop, widthPx).also { cache.put(key, it) }
    }

    private suspend fun ownBox(page: Int): CropBox? {
        boxLock.withLock { if (ownBoxes.containsKey(page)) return ownBoxes[page] }
        val luma = document.luminance(page)
        val box = MarginDetector.contentBox(luma.values, luma.width, luma.height)
        boxLock.withLock { ownBoxes[page] = box }
        return box
    }
}
