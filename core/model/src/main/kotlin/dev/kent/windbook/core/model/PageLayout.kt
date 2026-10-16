package dev.kent.windbook.core.model

/** A rectangle in fractions of the analysed area: x of its width, y of its height. */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

sealed interface LayoutBlock {
    /** One source line of text. [baseline] lies between [top] and [bottom]; each word spans the whole line height. */
    data class TextLine(
        val top: Float,
        val bottom: Float,
        val baseline: Float,
        val words: List<PageRect>,
        val startsParagraph: Boolean,
    ) : LayoutBlock

    /** A picture, table or formula kept as one image. */
    data class Figure(val box: PageRect) : LayoutBlock
}

/**
 * One page's crop box in reading order (spec §4.1). [aspect] is height / width of the analysed area and
 * [medianLineHeight] a fraction of its height (0 when the page has no text).
 */
data class PageLayout(val blocks: List<LayoutBlock>, val aspect: Float, val medianLineHeight: Float) {
    val textLines: List<LayoutBlock.TextLine> get() = blocks.filterIsInstance<LayoutBlock.TextLine>()
}
