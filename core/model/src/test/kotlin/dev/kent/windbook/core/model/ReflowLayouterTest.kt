package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflowLayouterTest {
    private val aspect = 1.5f

    /** A line at [top] of [words] words, each 0.1 wide; line height 0.015, baseline at 0.012. */
    private fun line(top: Float, words: Int = 5, startsParagraph: Boolean = false, wordWidth: Float = 0.1f) =
        LayoutBlock.TextLine(
            top = top,
            bottom = top + 0.015f,
            baseline = top + 0.012f,
            words = List(words) { i -> PageRect(0.05f + i * (wordWidth + 0.02f), top, 0.05f + i * (wordWidth + 0.02f) + wordWidth, top + 0.015f) },
            startsParagraph = startsParagraph,
        )

    private fun page(vararg blocks: LayoutBlock) = PageLayout(blocks.toList(), aspect, 0.015f)

    private val tenLines = page(*Array(10) { line(0.1f + it * 0.02f) })

    @Test fun noPieceGoesBeyondTheWidth() {
        val result = ReflowLayouter.layout(tenLines, targetWidth = 300f, lineHeight = 24f)
        assertTrue(result.pieces.all { it.x >= 0f && it.x + it.width <= 300.01f })
    }

    @Test fun wordOrderIsKept() {
        val pieces = ReflowLayouter.layout(tenLines, 300f, 24f).pieces
        assertEquals(50, pieces.size)
        val rows = pieces.zipWithNext().all { (a, b) -> b.y > a.y || (b.y == a.y && b.x > a.x) }
        assertTrue(rows)
    }

    @Test fun largerTextMakesMoreRows() {
        val small = ReflowLayouter.layout(tenLines, 300f, 18f).pieces.map { it.y }.distinct().size
        val large = ReflowLayouter.layout(tenLines, 300f, 32f).pieces.map { it.y }.distinct().size
        assertTrue("rows $small vs $large", large > small)
    }

    @Test fun textIsScaledToTheRequestedLineHeight() {
        // Line height 0.015 of a 1.5 aspect area = 0.0225 widths; 24 px per line → 1066.7 px per width.
        val result = ReflowLayouter.layout(tenLines, 300f, 24f)
        assertEquals(24f / 0.0225f, result.scale, 0.1f)
        assertEquals(24f, result.pieces[0].height, 0.01f)
    }

    @Test fun paragraphStartIsIndented() {
        val result = ReflowLayouter.layout(page(line(0.1f, words = 2), line(0.12f, words = 2, startsParagraph = true)), 600f, 24f)
        assertEquals(0f, result.pieces[0].x, 0.01f)
        assertEquals(1.5f * 24f, result.pieces[2].x, 0.01f)
    }

    @Test fun overWideWordIsScaledToFit() {
        val wide = page(line(0.1f, words = 1, wordWidth = 0.5f))
        val piece = ReflowLayouter.layout(wide, 300f, 24f).pieces.single()
        assertEquals(300f, piece.width, 0.01f)
        assertEquals(0f, piece.x, 0.01f)
    }

    @Test fun figureFitsTheWidthAndIsNeverEnlarged() {
        val big = ReflowLayouter.layout(page(line(0.1f), LayoutBlock.Figure(PageRect(0.1f, 0.3f, 0.9f, 0.6f))), 300f, 24f)
        assertEquals(300f, big.pieces.last().width, 0.01f)
        val small = ReflowLayouter.layout(page(line(0.1f), LayoutBlock.Figure(PageRect(0.1f, 0.3f, 0.12f, 0.31f))), 300f, 24f)
        assertEquals(0.02f * small.scale, small.pieces.last().width, 0.01f)
    }

    @Test fun wordsFromLinesWithDifferentMarksShareABaseline() {
        val plain = line(0.1f, words = 1)
        // A line with tone marks starts higher, so its baseline sits further below its top.
        val marked = LayoutBlock.TextLine(0.115f, 0.135f, 0.132f, listOf(PageRect(0.05f, 0.115f, 0.15f, 0.135f)), false)
        val pieces = ReflowLayouter.layout(page(plain, marked), 600f, 24f).pieces
        val scale = 24f / 0.0225f
        val baseline0 = pieces[0].y + (plain.baseline - plain.top) * aspect * scale
        val baseline1 = pieces[1].y + (marked.baseline - marked.top) * aspect * scale
        assertEquals(baseline0, baseline1, 0.01f)
    }

    @Test fun sourceBitmapIsCappedAtTwiceTheScreen() {
        assertEquals(2160, ReflowLayouter.sourceWidth(5000f, 1080))
        assertEquals(900, ReflowLayouter.sourceWidth(900f, 1080))
    }
}
