package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnalyzerTest {
    /** A synthetic 600×800 page; letters are 8×12 hollow boxes (1 px stroke) 2 px apart, words 10 px apart. */
    private class Page(val w: Int = 600, val h: Int = 800, paper: Int = 255) {
        val px = ByteArray(w * h) { paper.toByte() }

        fun rect(l: Int, t: Int, r: Int, b: Int, ink: Int = 0) {
            for (y in t until b) for (x in l until r) px[y * w + x] = ink.toByte()
        }

        private fun letter(x: Int, top: Int, ink: Int) {
            rect(x, top, x + 8, top + 1, ink)
            rect(x, top + 11, x + 8, top + 12, ink)
            rect(x, top, x + 1, top + 12, ink)
            rect(x + 7, top, x + 8, top + 12, ink)
        }

        /** Words of the given letter counts from [x]; returns the right edge (exclusive). */
        fun line(x: Int, top: Int, words: List<Int>, ink: Int = 0): Int {
            var cx = x
            for (n in words) {
                repeat(n) { letter(cx, top, ink); cx += 10 }
                cx += 8 // the last letter's 2 px gap + 8 = a 10 px word gap
            }
            return cx - 10
        }

        /** A heading line of 16×24 hollow letters (2 px stroke) 6 px apart: twice the body size, still text. */
        fun heading(x: Int, top: Int, letters: Int) {
            var cx = x
            repeat(letters) {
                rect(cx, top, cx + 16, top + 2)
                rect(cx, top + 22, cx + 16, top + 24)
                rect(cx, top, cx + 2, top + 24)
                rect(cx + 14, top, cx + 16, top + 24)
                cx += 22
            }
        }

        fun analyze() = PageAnalyzer.analyze(px, w, h)
    }

    @Test fun findsLinesAndWordsInReadingOrder() {
        val page = Page()
        for (i in 0 until 5) page.line(40, 100 + i * 20, listOf(3, 4, 2))
        val lines = page.analyze().textLines
        assertEquals(5, lines.size)
        assertTrue(lines.all { it.words.size == 3 })
        assertEquals(40 / 600f, lines[0].words[0].left, 1e-4f)
        assertEquals(68 / 600f, lines[0].words[0].right, 1e-4f)
        assertEquals(lines.map { it.top }.sorted(), lines.map { it.top })
    }

    @Test fun detachedMarkJoinsTheLineBelow() {
        val page = Page()
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(3, 4, 2))
        page.rect(40, 114, 43, 117) // a tone mark 3 px above line 2 (2 px below line 1)
        val lines = page.analyze().textLines
        assertEquals(3, lines.size)
        assertEquals(114 / 800f, lines[1].top, 1e-4f)
    }

    @Test fun twoColumnsReadLeftThenRight() {
        val page = Page()
        for (i in 0 until 10) {
            page.line(40, 100 + i * 20, listOf(5, 5, 5))
            page.line(330, 100 + i * 20, listOf(5, 5, 5))
        }
        val lines = page.analyze().textLines
        assertEquals(20, lines.size)
        assertTrue(lines.take(10).all { it.words.first().left < 0.5f })
        assertTrue(lines.drop(10).all { it.words.first().left > 0.5f })
    }

    @Test fun figureBlockStaysWhole() {
        val page = Page()
        page.line(40, 100, listOf(5, 5, 5))
        page.line(40, 120, listOf(5, 5, 5))
        page.rect(40, 150, 300, 300)
        page.line(40, 320, listOf(5, 5, 5))
        page.line(40, 340, listOf(5, 5, 5))
        val blocks = page.analyze().blocks
        assertEquals(listOf("T", "T", "F", "T", "T"), blocks.map { if (it is LayoutBlock.Figure) "F" else "T" })
        val figure = (blocks[2] as LayoutBlock.Figure).box
        assertEquals(40 / 600f, figure.left, 1e-3f)
        assertEquals(150 / 800f, figure.top, 1e-3f)
        assertEquals(300 / 600f, figure.right, 1e-3f)
        assertEquals(300 / 800f, figure.bottom, 1e-3f)
    }

    @Test fun indentStartsAParagraph() {
        val page = Page()
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(5, 5, 5))
        page.line(70, 160, listOf(5, 5, 4))
        val starts = page.analyze().textLines.map { it.startsParagraph }
        assertEquals(listOf(false, false, false, true), starts)
    }

    @Test fun largeGapStartsAParagraph() {
        val page = Page()
        for (top in listOf(100, 120, 140, 180)) page.line(40, top, listOf(5, 5, 5))
        assertEquals(listOf(false, false, false, true), page.analyze().textLines.map { it.startsParagraph })
    }

    @Test fun greyScanPaperIsNotInk() {
        val page = Page(paper = 225)
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(3, 3), ink = 150)
        assertEquals(3, page.analyze().textLines.size)
    }

    @Test fun blankPageIsOneFigure() =
        assertEquals(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), Page().analyze().blocks)

    @Test fun fullBleedPhotoIsOneFigure() {
        val page = Page()
        for (y in 0 until page.h) for (x in 0 until page.w) page.px[y * page.w + x] = ((x + y) % 256).toByte()
        assertEquals(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), page.analyze().blocks)
    }

    @Test fun touchingLinesAreSplitAtTheLightestRow() {
        val page = Page()
        for (i in 0 until 6) page.line(40, 100 + i * 20, listOf(5, 5, 5))
        page.line(40, 240, listOf(5, 5, 5))
        page.rect(40, 252, 42, 253) // a descender touching the next line
        page.line(40, 253, listOf(5, 5, 5))
        assertEquals(8, page.analyze().textLines.size)
    }

    @Test fun baselineIsTheBottomOfTheLetters() {
        val page = Page()
        page.line(40, 100, listOf(3))
        page.line(40, 120, listOf(3))
        assertEquals(112 / 800f, page.analyze().textLines[0].baseline, 1e-4f)
    }

    /** Found on the scanned textbook's title page: a heading's tone marks are taller than half a body line. */
    @Test fun markOfALargeHeadingJoinsTheHeading() {
        val page = Page()
        for (i in 0 until 5) page.line(40, 100 + i * 20, listOf(5, 5, 5))
        page.heading(40, 236, 6)
        page.rect(50, 224, 52, 232) // two 8 px tone-mark strokes, 4 px above the heading
        page.rect(130, 224, 132, 232)
        for (i in 0 until 3) page.line(40, 300 + i * 20, listOf(5, 5, 5))
        fun top(b: LayoutBlock) = if (b is LayoutBlock.Figure) b.box.top else (b as LayoutBlock.TextLine).top
        val heading = page.analyze().blocks.single { top(it) in (200 / 800f)..(260 / 800f) }
        assertEquals(224 / 800f, top(heading), 1e-3f)
    }

    /** Stacked marks (Ẩ, Ễ, Ậ: a circumflex plus a tone mark) are two thin runs; both must join the line. */
    @Test fun stackedMarksBothJoinTheLine() {
        val page = Page()
        for (top in listOf(100, 130, 160)) page.line(40, top, listOf(3, 4, 2))
        page.rect(40, 124, 47, 127) // circumflex, 3 px above line 2
        page.rect(42, 119, 45, 122) // tone mark, 2 px above the circumflex
        val blocks = page.analyze().blocks
        assertTrue("blocks: $blocks", blocks.all { it is LayoutBlock.TextLine })
        assertEquals(3, blocks.size)
        assertEquals(119 / 800f, (blocks[1] as LayoutBlock.TextLine).top, 1e-4f)
    }
}
