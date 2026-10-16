package dev.kent.windbook.core.model

enum class PdfLayout { VERTICAL, PAGED }

/** Page size in PDF points. */
data class PageSize(val width: Float, val height: Float) {
    val aspect: Float get() = width / height
}

/** Highest zoom a PDF page can be pinched to. */
const val MAX_PDF_ZOOM = 4f

/** Reflow text sizes as on-screen line height in dp (spec §4.3). */
object ReflowSizes {
    val LINE_HEIGHTS_DP = listOf(18, 21, 24, 28, 32, 38, 44)
    const val DEFAULT = 2
    const val LAST = 6

    fun lineHeightDp(level: Int): Int = LINE_HEIGHTS_DP[level.coerceIn(0, LAST)]
}

/**
 * Per-book display choices; null means "use the default". [zoom] and [zoomX] are the vertical-mode zoom and
 * horizontal scroll (0..1 of the scrollable width); [reflowSize] indexes [ReflowSizes.LINE_HEIGHTS_DP].
 */
data class BookDisplayPrefs(
    val pdfLayout: PdfLayout? = null,
    val cropMargins: Boolean? = null,
    val zoom: Float? = null,
    val zoomX: Float? = null,
    val reflowSize: Int? = null,
)

data class EffectiveDisplay(
    val layout: PdfLayout,
    val cropMargins: Boolean,
    val zoom: Float = 1f,
    val zoomX: Float = 0f,
    val reflowSize: Int = ReflowSizes.DEFAULT,
)

/** Landscape books (slides) read page by page; everything else scrolls. Median of pages 1–5. */
fun defaultPdfLayout(firstPages: List<PageSize>): PdfLayout {
    if (firstPages.isEmpty()) return PdfLayout.VERTICAL
    val aspects = firstPages.take(5).map { it.aspect }.sorted()
    return if (aspects[aspects.size / 2] > 1f) PdfLayout.PAGED else PdfLayout.VERTICAL
}

/** Margins are cropped by default except page by page; saved values are clamped to what the view supports. */
fun BookDisplayPrefs.resolve(firstPages: List<PageSize>): EffectiveDisplay {
    val layout = pdfLayout ?: defaultPdfLayout(firstPages)
    return EffectiveDisplay(
        layout = layout,
        cropMargins = cropMargins ?: (layout != PdfLayout.PAGED),
        zoom = (zoom?.takeIf { it.isFinite() } ?: 1f).coerceIn(1f, MAX_PDF_ZOOM),
        zoomX = (zoomX?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f),
        reflowSize = (reflowSize ?: ReflowSizes.DEFAULT).coerceIn(0, ReflowSizes.LAST),
    )
}
