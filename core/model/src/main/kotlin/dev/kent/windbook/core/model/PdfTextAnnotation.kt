package dev.kent.windbook.core.model

/** Full-page points use the framework renderer's upper-left origin. */
data class PdfPoint(val x: Float, val y: Float)
data class PdfTextRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(point: PdfPoint): Boolean = point.x in left..right && point.y in top..bottom
}

data class PdfTextAnchor(val version: Int = 1, val page: Int, val width: Float, val height: Float, val rects: List<PdfTextRect>, val quote: String) {
    fun isValid(): Boolean = version == 1 && page >= 0 && width.isFinite() && height.isFinite() &&
        width > 0 && height > 0 && quote.isNotBlank() && quote.length <= MAX_QUOTE &&
        rects.isNotEmpty() && rects.size <= MAX_RECTS && rects.all {
            listOf(it.left,it.top,it.right,it.bottom).all { n -> n.isFinite() && n in 0f..1f } &&
                it.left < it.right && it.top < it.bottom
        }
    companion object { const val MAX_QUOTE = 16_384; const val MAX_RECTS = 512 }
}

enum class AnnotationColor { GOLD, GREEN, BLUE, PINK, PURPLE }

data class PdfTextAnnotation(
    val id: String, val bookId: String, val fingerprint: String, val anchor: PdfTextAnchor?,
    val quote: String, val color: AnnotationColor, val body: String?, val createdAt: Long, val updatedAt: Long,
    val bookTitle: String = "",
)

/** Display dimensions include the rounded displayed height and current zoom, independent of bitmap size. */
class PdfPageGeometry(val size: PageSize, val crop: CropBox, val width: Float, val height: Float) {
    fun toPage(point: PdfPoint): PdfPoint? {
        if (!point.x.isFinite() || !point.y.isFinite() || point.x !in 0f..width || point.y !in 0f..height || width <= 0 || height <= 0) return null
        return PdfPoint(crop.left*size.width + point.x*crop.width*size.width/width,
            crop.top*size.height + point.y*crop.height*size.height/height)
    }
    fun toDisplay(point: PdfPoint): PdfPoint = PdfPoint(
        (point.x-crop.left*size.width)*width/(crop.width*size.width),
        (point.y-crop.top*size.height)*height/(crop.height*size.height),
    )
}

/** Ephemeral handle endpoints are full-page points; only the anchor is persisted. */
data class PdfTextSelection(val anchor: PdfTextAnchor, val start: PdfPoint, val end: PdfPoint)

/** Put the first selected line in the visible upper quarter, without scrolling into the next page. */
fun pdfRevealScrollOffset(anchor: PdfTextAnchor, pageHeightPx: Int, viewportHeightPx: Int): Int =
    (anchor.rects.first().top*pageHeightPx-viewportHeightPx*.25f).toInt()
        .coerceIn(0,(pageHeightPx-viewportHeightPx).coerceAtLeast(0))

/** Popup window coordinates and safe viewport bounds are pixels; density is pixels per dp. */
fun pdfSelectionPopupY(selectionTop: Float, selectionBottom: Float, popupHeight: Int,
    viewportTop: Int, viewportBottom: Int, density: Float, handleScale: Float = 1f): Int {
    val above=(selectionTop-popupHeight-12f*density).toInt()
    val below=(selectionBottom+(24f*handleScale+12f)*density).toInt()
    val bottom=(viewportBottom-popupHeight).coerceAtLeast(viewportTop)
    return (if(above>=viewportTop) above else below).coerceIn(viewportTop,bottom)
}
