package dev.kent.windbook.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Copy [src] (fractions of the crop box) to the px rectangle at ([x], [y]) sized [width]×[height]. */
data class ReflowPiece(val src: PageRect, val x: Float, val y: Float, val width: Float, val height: Float)

/** One page re-wrapped to the screen. [scale] is px per analysed width: the crop box would be [scale] px wide. */
data class ReflowPage(val height: Float, val pieces: List<ReflowPiece>, val scale: Float)

/** Places a page's word images into rows of the screen width at the chosen text size (spec §4.2). */
object ReflowLayouter {
    const val LINE_SPACING = 1.3f
    const val WORD_GAP = 0.35f
    const val PARAGRAPH_SPACE = 0.5f
    const val INDENT = 1.5f

    private class Placed(val src: PageRect, val x: Float, val ascent: Float, val width: Float, val height: Float)

    fun layout(page: PageLayout, targetWidth: Float, lineHeight: Float): ReflowPage {
        val scale = if (page.medianLineHeight > 0f) lineHeight / (page.medianLineHeight * page.aspect) else targetWidth
        val pieces = mutableListOf<ReflowPiece>()
        val row = mutableListOf<Placed>()
        var y = 0f
        var x = 0f
        var first = true

        fun flush() {
            if (row.isEmpty()) return
            val ascent = row.maxOf { it.ascent }
            val descent = row.maxOf { it.height - it.ascent }
            for (p in row) pieces += ReflowPiece(p.src, p.x, y + ascent - p.ascent, p.width, p.height)
            y += max(lineHeight * LINE_SPACING, ascent + descent + lineHeight * 0.3f)
            row.clear()
            x = 0f
        }

        for (block in page.blocks) {
            when (block) {
                is LayoutBlock.TextLine -> {
                    if (block.startsParagraph) {
                        flush()
                        if (!first) y += lineHeight * PARAGRAPH_SPACE
                        x = lineHeight * INDENT
                    }
                    first = false
                    val lineHeightPx = (block.bottom - block.top) * page.aspect * scale
                    val ascentPx = (block.baseline - block.top) * page.aspect * scale
                    for (word in block.words) {
                        var width = word.width * scale
                        var height = lineHeightPx
                        var ascent = ascentPx
                        if (width > targetWidth) {
                            val f = targetWidth / width
                            width *= f
                            height *= f
                            ascent *= f
                        }
                        if (x + width > targetWidth) {
                            if (row.isNotEmpty()) flush() else x = 0f
                        }
                        row += Placed(PageRect(word.left, block.top, word.right, block.bottom), x, ascent, width, height)
                        x += width + lineHeight * WORD_GAP
                    }
                }
                is LayoutBlock.Figure -> {
                    flush()
                    if (!first) y += lineHeight * PARAGRAPH_SPACE
                    first = false
                    val natural = block.box.width * scale
                    val f = min(1f, targetWidth / natural)
                    val width = natural * f
                    val height = block.box.height * page.aspect * scale * f
                    pieces += ReflowPiece(block.box, (targetWidth - width) / 2f, y, width, height)
                    y += height + lineHeight * PARAGRAPH_SPACE
                }
            }
        }
        flush()
        return ReflowPage(y, pieces, scale)
    }

    /** Width to render the crop box at for [scale], capped at twice the screen so memory stays bounded. */
    fun sourceWidth(scale: Float, viewportWidth: Int): Int = scale.roundToInt().coerceIn(1, 2 * viewportWidth)
}
