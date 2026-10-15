package dev.kent.windbook.core.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Part of a page to show, as fractions of its width/height (0..1). */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun union(other: CropBox) =
        CropBox(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** Never removes more than [MarginDetector.MAX_CROP_PER_SIDE] of any side. */
    fun clamped(): CropBox {
        val m = MarginDetector.MAX_CROP_PER_SIDE
        return CropBox(left.coerceIn(0f, m), top.coerceIn(0f, m), right.coerceIn(1f - m, 1f), bottom.coerceIn(1f - m, 1f))
    }

    companion object {
        val FULL = CropBox(0f, 0f, 1f, 1f)
    }
}

/** Book-level crop, one box per page parity (printed books mirror their margins). */
data class CropPlan(val odd: CropBox, val even: CropBox) {
    /** [pageIndex] is 0-based, so index 0 is page 1 (odd). */
    fun boxFor(pageIndex: Int): CropBox = if (pageIndex % 2 == 0) odd else even

    companion object {
        val NONE = CropPlan(CropBox.FULL, CropBox.FULL)
    }
}

object MarginDetector {
    const val INK_THRESHOLD = 200
    const val PADDING = 0.015f
    const val MAX_CROP_PER_SIDE = 0.25f
    const val MIN_CONTENT = 0.40f

    /** Content box of a page given as luminance bytes (0 = black, 255 = white), or null when the page is blank. */
    fun contentBox(luma: ByteArray, width: Int, height: Int): CropBox? {
        val rowInk = IntArray(height)
        val colInk = IntArray(width)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((luma[row + x].toInt() and 0xFF) < INK_THRESHOLD) {
                    rowInk[y]++
                    colInk[x]++
                }
            }
        }
        val rowMin = max(2, ceil(width * 0.003).toInt())
        val colMin = max(2, ceil(height * 0.003).toInt())
        val top = rowInk.indexOfFirst { it >= rowMin }
        if (top < 0) return null
        val bottom = rowInk.indexOfLast { it >= rowMin }
        val left = colInk.indexOfFirst { it >= colMin }
        val right = colInk.indexOfLast { it >= colMin }
        if (left < 0) return null
        return CropBox(
            (left.toFloat() / width - PADDING).coerceAtLeast(0f),
            (top.toFloat() / height - PADDING).coerceAtLeast(0f),
            ((right + 1).toFloat() / width + PADDING).coerceAtMost(1f),
            ((bottom + 1).toFloat() / height + PADDING).coerceAtMost(1f),
        )
    }

    /** Wide edges (10th/90th percentile) of the usable samples, so a wide page is rarely cut. */
    fun bookBox(samples: List<CropBox?>): CropBox {
        val boxes = samples.filterNotNull().filter { it.width >= MIN_CONTENT && it.height >= MIN_CONTENT }
        if (boxes.isEmpty()) return CropBox.FULL
        fun pct(values: List<Float>, p: Float): Float = values.sorted()[((values.size - 1) * p).roundToInt()]
        return CropBox(
            pct(boxes.map { it.left }, 0.1f),
            pct(boxes.map { it.top }, 0.1f),
            pct(boxes.map { it.right }, 0.9f),
            pct(boxes.map { it.bottom }, 0.9f),
        ).clamped()
    }

    /** The book box, grown to include this page's own content so nothing is ever cut. */
    fun forPage(book: CropBox, own: CropBox?): CropBox = if (own == null) book else book.union(own).clamped()

    /** Up to [perParity] 0-based page indices per parity, evenly spread, skipping covers and back matter. */
    fun sampleIndices(pageCount: Int, perParity: Int = 12): List<Int> {
        val range = if (pageCount <= 8) (0 until pageCount).toList() else (4 until pageCount - 2).toList()
        fun spread(list: List<Int>): List<Int> =
            if (list.size <= perParity) list else List(perParity) { i -> list[i * (list.size - 1) / (perParity - 1)] }
        return (spread(range.filter { it % 2 == 0 }) + spread(range.filter { it % 2 == 1 })).sorted()
    }
}
