package dev.kent.windbook.core.model

import kotlin.math.roundToInt

/** UI seek fractions include the last page; saved PDF progression keeps its legacy formula. */
fun seekPage(fraction: Double, count: Int): Int? =
    if (!fraction.isFinite() || count <= 0) null
    else (fraction.coerceIn(0.0, 1.0) * (count - 1)).roundToInt()

class ReaderJumpHistory {
    private val origins = ArrayDeque<String>()
    val size: Int get() = origins.size
    fun record(origin: String?, destination: String) {
        if (origin == null || origin == destination) return
        if (origins.size == 10) origins.removeFirst()
        origins.addLast(origin)
    }
    fun peek(): String? = origins.lastOrNull()
    fun pop(): String? = origins.removeLastOrNull()
    fun clear() = origins.clear()
}

data class ContentsEntry(val id: String, val title: String, val page: Int? = null,
    val children: List<ContentsEntry> = emptyList(), val locatorJson: String? = null)
enum class ContentsStatus { READY, EMPTY, UNAVAILABLE }
data class PdfContents(val status: ContentsStatus, val entries: List<ContentsEntry> = emptyList())

/** Bounds our cache/tree independently of the upstream metadata parser. */
fun normalizePdfContents(entries: List<ContentsEntry>, pageCount: Int): PdfContents {
    var count = 0
    var chars = 0
    fun visit(nodes: List<ContentsEntry>, depth: Int): List<ContentsEntry> {
        require(depth <= 32)
        return nodes.map { node ->
            require(++count <= 10000 && node.title.length <= 1024)
            chars += node.title.length + node.id.length + 64
            require(chars <= 200000)
            node.copy(page = node.page?.takeIf { it in 1..pageCount }, children = visit(node.children, depth + 1))
        }
    }
    return try {
        val clean = visit(entries, 0)
        PdfContents(if (clean.isEmpty()) ContentsStatus.EMPTY else ContentsStatus.READY, clean)
    } catch (_: IllegalArgumentException) { PdfContents(ContentsStatus.UNAVAILABLE) }
}
