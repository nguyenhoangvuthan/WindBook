package dev.kent.windbook.core.model

enum class PdfLayout { VERTICAL, PAGED }

/** Page size in PDF points. */
data class PageSize(val width: Float, val height: Float) {
    val aspect: Float get() = width / height
}

/** Per-book display choices; null means "use the default". */
data class BookDisplayPrefs(val pdfLayout: PdfLayout? = null, val cropMargins: Boolean? = null)

data class EffectiveDisplay(val layout: PdfLayout, val cropMargins: Boolean)

/** Landscape books (slides) read page by page; everything else scrolls. Median of pages 1–5. */
fun defaultPdfLayout(firstPages: List<PageSize>): PdfLayout {
    if (firstPages.isEmpty()) return PdfLayout.VERTICAL
    val aspects = firstPages.take(5).map { it.aspect }.sorted()
    return if (aspects[aspects.size / 2] > 1f) PdfLayout.PAGED else PdfLayout.VERTICAL
}

/** Margins are cropped by default only when scrolling; paged views show the whole page. */
fun BookDisplayPrefs.resolve(firstPages: List<PageSize>): EffectiveDisplay {
    val layout = pdfLayout ?: defaultPdfLayout(firstPages)
    return EffectiveDisplay(layout, cropMargins ?: (layout == PdfLayout.VERTICAL))
}
