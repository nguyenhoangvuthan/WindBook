package dev.kent.windbook.core.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Finds columns, text lines, words and figures in a page image (spec §4.1). Pure, so it is tested on the JVM. */
object PageAnalyzer {
    const val MIN_GUTTER = 0.03f
    const val GUTTER_SPAN = 0.8f
    const val MIN_COLUMN = 0.2f
    const val MAX_COLUMNS = 3
    const val TALL_LINE = 1.6f
    const val SPLIT_INK = 0.15f
    const val MARK_HEIGHT = 0.5f
    const val MARK_DISTANCE = 0.35f
    const val FIGURE_HEIGHT = 2.5f
    const val FIGURE_PAGE_SHARE = 0.25f
    const val FIGURE_DENSITY = 0.45f
    const val X_HEIGHT_INK = 0.4f
    const val WORD_GAP = 0.35f
    const val PARAGRAPH_GAP = 1.5f
    const val PARAGRAPH_INDENT = 0.04f
    const val SHORT_LINE = 0.8f

    fun analyze(luma: ByteArray, width: Int, height: Int): PageLayout {
        val wholePage = PageLayout(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), height.toFloat() / width, 0f)
        val threshold = otsu(luma) ?: return wholePage
        val ink = Ink(luma, width, height, threshold)
        val inkRows = (0 until height).filter { ink.rowCount(it, 0, width - 1) > 0 }
        if (inkRows.isEmpty()) return wholePage
        val blocks = mutableListOf<LayoutBlock>()
        val lineHeights = mutableListOf<Int>()
        for (column in columns(ink, inkRows.first(), inkRows.last())) {
            Column(ink, column).let { blocks += it.blocks; lineHeights += it.textLineHeights }
        }
        if (lineHeights.isEmpty()) return wholePage
        return PageLayout(blocks, height.toFloat() / width, median(lineHeights).toFloat() / height)
    }

    /** Otsu's threshold: luminance below it is ink. Null for a flat page (blank, or one solid tone). */
    internal fun otsu(luma: ByteArray): Int? {
        val hist = IntArray(256)
        for (b in luma) hist[b.toInt() and 0xFF]++
        val lo = hist.indexOfFirst { it > 0 }
        val hi = hist.indexOfLast { it > 0 }
        if (hi - lo < 32) return null
        val total = luma.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var weightBack = 0.0
        var sumBack = 0.0
        var best = -1.0
        var threshold = 128
        for (i in 0..255) {
            weightBack += hist[i]
            if (weightBack == 0.0) continue
            val weightFore = total - weightBack
            if (weightFore == 0.0) break
            sumBack += i * hist[i].toDouble()
            val meanBack = sumBack / weightBack
            val meanFore = (sumAll - sumBack) / weightFore
            val between = weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold + 1
    }

    /** Splits the content at white gutters at least [MIN_GUTTER] wide and [GUTTER_SPAN] tall. */
    private fun columns(ink: Ink, top: Int, bottom: Int): List<IntRange> {
        val left = (0 until ink.width).first { ink.colCount(it, top, bottom) > 0 }
        val right = (0 until ink.width).last { ink.colCount(it, top, bottom) > 0 }
        val span = bottom - top + 1
        val gutters = mutableListOf<IntRange>()
        var x = left
        while (x <= right) {
            if (ink.longestWhiteRun(x, top, bottom) >= GUTTER_SPAN * span) {
                val start = x
                while (x <= right && ink.longestWhiteRun(x, top, bottom) >= GUTTER_SPAN * span) x++
                gutters += start until x
            } else {
                x++
            }
        }
        val minColumn = MIN_COLUMN * (right - left + 1)
        val splits = mutableListOf<Int>()
        for (gutter in gutters.filter { it.count() >= MIN_GUTTER * ink.width }.sortedByDescending { it.count() }) {
            if (splits.size == MAX_COLUMNS - 1) break
            val bounds = (listOf(left, right) + splits + ((gutter.first + gutter.last) / 2)).sorted()
            if (bounds.zipWithNext().all { (a, b) -> b - a >= minColumn }) splits += (gutter.first + gutter.last) / 2
        }
        return (listOf(left) + splits.sorted() + (right + 1)).zipWithNext { a, b -> a until b }
    }

    /** Lines, words, figures and paragraphs of one column. */
    private class Column(private val ink: Ink, private val xs: IntRange) {
        val blocks = mutableListOf<LayoutBlock>()
        val textLineHeights = mutableListOf<Int>()
        private val rowInk = IntArray(ink.height) { ink.rowCount(it, xs.first, xs.last) }

        init {
            val minRowInk = max(1, (0.002f * xs.count()).roundToInt())
            val runs = mutableListOf<IntRange>()
            var y = 0
            while (y < ink.height) {
                if (rowInk[y] >= minRowInk) {
                    val start = y
                    while (y < ink.height && rowInk[y] >= minRowInk) y++
                    runs += start until y
                } else {
                    y++
                }
            }
            if (runs.isNotEmpty()) build(runs)
        }

        private fun build(runs: List<IntRange>) {
            val h = robustMedian(runs.map { it.count() })
            val lines = mergeMarks(runs.flatMap { splitTall(it, h) }, h)
            val isFigure = lines.map { line -> figureLine(line, h) }
            // Consecutive figure lines (gap ≤ h) form one figure; text lines are kept for words and paragraphs.
            val groups = mutableListOf<Pair<Boolean, MutableList<IntRange>>>()
            lines.forEachIndexed { i, line ->
                val last = groups.lastOrNull()
                if (isFigure[i] && last != null && last.first && line.first - last.second.last().last - 1 <= h) {
                    last.second += line
                } else {
                    groups += isFigure[i] to mutableListOf(line)
                }
            }
            val xHeights = groups.filter { !it.first }.flatMap { it.second }.map { xBand(it).let { (a, b) -> b - a + 1 } }
            val wordGap = max(1f, WORD_GAP * (if (xHeights.isEmpty()) h else median(xHeights)))
            val textBlocks = mutableListOf<Pair<IntRange, List<IntRange>>>()
            val order = mutableListOf<Any>() // IntRange for text lines (resolved below), PageRect for figures
            for ((figure, members) in groups) {
                if (figure) {
                    val top = members.first().first
                    val bottom = members.last().last
                    val (l, r) = extent(top..bottom) ?: continue
                    order += PageRect(l.toFloat() / ink.width, top.toFloat() / ink.height, (r + 1f) / ink.width, (bottom + 1f) / ink.height)
                } else {
                    for (line in members) {
                        val words = words(line, wordGap)
                        if (words.isEmpty()) continue
                        textBlocks += line to words
                        order.add(line) // add(): an IntRange is Iterable, so += would append its numbers
                    }
                }
            }
            val starts = paragraphStarts(order, textBlocks)
            for (item in order) {
                if (item is PageRect) {
                    blocks += LayoutBlock.Figure(item)
                } else {
                    val line = item as IntRange
                    val words = textBlocks.first { it.first == line }.second
                    val top = line.first.toFloat() / ink.height
                    val bottom = (line.last + 1f) / ink.height
                    blocks += LayoutBlock.TextLine(
                        top = top,
                        bottom = bottom,
                        baseline = (xBand(line).second + 1f) / ink.height,
                        words = words.map { PageRect(it.first.toFloat() / ink.width, top, (it.last + 1f) / ink.width, bottom) },
                        startsParagraph = starts.getValue(line),
                    )
                    textLineHeights += line.count()
                }
            }
        }

        /** Touching lines are cut at their lightest inner row when it is nearly empty. */
        private fun splitTall(run: IntRange, h: Int): List<IntRange> {
            if (run.count() <= TALL_LINE * h || run.count() < 5) return listOf(run)
            val peak = run.maxOf { rowInk[it] }
            val lightest = ((run.first + 2)..(run.last - 2)).minByOrNull { rowInk[it] } ?: return listOf(run)
            if (rowInk[lightest] > SPLIT_INK * peak) return listOf(run)
            return splitTall(run.first until lightest, h) + splitTall((lightest + 1)..run.last, h)
        }

        /** Thin runs near a line (detached diacritics) join it; the line below wins ties. */
        private fun mergeMarks(lines: List<IntRange>, h: Int): List<IntRange> {
            val out = lines.toMutableList()
            val reach = MARK_DISTANCE * h
            var i = 0
            while (i < out.size) {
                val line = out[i]
                if (line.count() >= MARK_HEIGHT * h) {
                    i++
                    continue
                }
                val below = out.getOrNull(i + 1)?.let { it.first - line.last - 1 }
                val above = out.getOrNull(i - 1)?.let { line.first - it.last - 1 }
                when {
                    below != null && below <= reach -> {
                        out[i + 1] = line.first..out[i + 1].last
                        out.removeAt(i)
                    }
                    above != null && above <= reach -> {
                        out[i - 1] = out[i - 1].first..line.last
                        out.removeAt(i)
                        i--
                    }
                    else -> i++
                }
            }
            return out
        }

        private fun figureLine(line: IntRange, h: Int): Boolean {
            val (l, r) = extent(line) ?: return true
            val inkSum = line.sumOf { rowInk[it] }
            val density = inkSum.toFloat() / (line.count() * (r - l + 1))
            return line.count() > FIGURE_HEIGHT * h ||
                line.count() > FIGURE_PAGE_SHARE * ink.height ||
                density > FIGURE_DENSITY ||
                line.count() < MARK_HEIGHT * h
        }

        /** First and last rows holding ≥ [X_HEIGHT_INK] of the line's peak row ink: the x-height band. */
        private fun xBand(line: IntRange): Pair<Int, Int> {
            val peak = line.maxOf { rowInk[it] }
            val strong = line.filter { rowInk[it] >= X_HEIGHT_INK * peak }
            return strong.first() to strong.last()
        }

        private fun extent(rows: IntRange): Pair<Int, Int>? {
            val cols = xs.filter { x -> ink.colCount(x, rows.first, rows.last) > 0 }
            return if (cols.isEmpty()) null else cols.first() to cols.last()
        }

        private fun words(line: IntRange, wordGap: Float): List<IntRange> {
            val inkCols = xs.filter { x -> ink.colCount(x, line.first, line.last) > 0 }
            if (inkCols.isEmpty()) return emptyList()
            val words = mutableListOf<IntRange>()
            var start = inkCols.first()
            var prev = start
            for (x in inkCols.drop(1)) {
                if (x - prev - 1 >= wordGap) {
                    words += start..prev
                    start = x
                }
                prev = x
            }
            words += start..prev
            return words
        }

        private fun paragraphStarts(order: List<Any>, text: List<Pair<IntRange, List<IntRange>>>): Map<IntRange, Boolean> {
            if (text.isEmpty()) return emptyMap()
            val lefts = text.map { it.second.first().first }
            val rights = text.map { it.second.last().last }
            val colLeft = percentile(lefts, 0.1f)
            val colWidth = (percentile(rights, 0.9f) - colLeft).coerceAtLeast(1)
            val gaps = text.zipWithNext { a, b -> b.first.first - a.first.last - 1 }.filter { it > 0 }
            val medianGap = if (gaps.isEmpty()) 0 else median(gaps)
            val result = HashMap<IntRange, Boolean>()
            var previous: Pair<IntRange, List<IntRange>>? = null
            var afterFigure = false
            for (item in order) {
                if (item is PageRect) {
                    afterFigure = true
                    continue
                }
                val current = text.first { it.first == item }
                val indent = current.second.first().first - colLeft > PARAGRAPH_INDENT * colWidth
                val prev = previous
                result[current.first] = indent || (prev != null && (
                    afterFigure ||
                        current.first.first - prev.first.last - 1 > PARAGRAPH_GAP * medianGap ||
                        prev.second.last().last < colLeft + SHORT_LINE * colWidth
                    ))
                previous = current
                afterFigure = false
            }
            return result
        }
    }

    private class Ink(luma: ByteArray, val width: Int, val height: Int, threshold: Int) {
        private val bits = BooleanArray(luma.size) { (luma[it].toInt() and 0xFF) < threshold }

        fun rowCount(y: Int, x0: Int, x1: Int): Int {
            var n = 0
            for (x in x0..x1) if (bits[y * width + x]) n++
            return n
        }

        fun colCount(x: Int, y0: Int, y1: Int): Int {
            var n = 0
            for (y in y0..y1) if (bits[y * width + x]) n++
            return n
        }

        fun longestWhiteRun(x: Int, y0: Int, y1: Int): Int {
            var best = 0
            var run = 0
            for (y in y0..y1) {
                if (bits[y * width + x]) {
                    run = 0
                } else {
                    run++
                    if (run > best) best = run
                }
            }
            return best
        }
    }

    private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]

    /** Median of the runs at least half the plain median, so tone marks don't drag it down. */
    private fun robustMedian(values: List<Int>): Int {
        val m = median(values)
        val tall = values.filter { it >= m / 2 }
        return if (tall.isEmpty()) m else median(tall)
    }

    private fun percentile(values: List<Int>, p: Float): Int = values.sorted()[((values.size - 1) * p).roundToInt()]
}
