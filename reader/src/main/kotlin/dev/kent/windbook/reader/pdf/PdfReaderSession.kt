package dev.kent.windbook.reader.pdf

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.toJsonString
import dev.kent.windbook.reader.toLocatorOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** 0-based page for a saved locator; Readium PDF positions are the 1-based page numbers. */
internal fun pageIndexOf(locator: Locator?, pageCount: Int): Int {
    val position = locator?.locations?.position ?: return 0
    return (position - 1).coerceIn(0, maxOf(0, pageCount - 1))
}

/** Readium keeps the publication (metadata, positions); [document] does the rendering. */
internal class PdfReaderSession(
    private val publication: Publication,
    private val document: PdfDocumentHandle,
    private val positions: List<Locator>,
) : PdfSession {
    override val format = BookFormat.PDF
    override val pageCount: Int get() = document.pageCount
    override val pages: PdfPageSource = PdfPages(document)

    override suspend fun firstPageSizes(): List<PageSize> = (0 until minOf(5, pageCount)).map { document.measure(it) }

    override fun locatorJsonFor(page: Int): String =
        positions[page.coerceIn(0, positions.lastIndex)].toJsonString()

    override fun pageOf(locatorJson: String?): Int = pageIndexOf(locatorJson?.toLocatorOrNull(), pageCount)

    override fun close() {
        document.close()
        publication.close()
    }
}
