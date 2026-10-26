package dev.kent.windbook.reader.pdf

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.toJsonString
import dev.kent.windbook.reader.toLocatorOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType

/** 0-based page for a saved locator; Readium PDF positions are the 1-based page numbers. */
internal fun pageIndexOf(locator: Locator?, pageCount: Int): Int {
    val position = locator?.locations?.position ?: return 0
    return (position - 1).coerceIn(0, maxOf(0, pageCount - 1))
}

/** Locator for 0-based [page], in the shape M1's Readium PDF navigator saved (constant href, 1-based position). */
internal fun pdfLocator(page: Int, pageCount: Int): Locator {
    val progression = page.toDouble() / pageCount
    return Locator(
        href = Url("publication.pdf")!!,
        mediaType = MediaType.PDF,
        locations = Locator.Locations(
            fragments = listOf("page=${page + 1}"),
            progression = progression,
            position = page + 1,
            totalProgression = progression,
        ),
    )
}

/**
 * A PDF read with WindBook's own renderer. It does not open the file through Readium: Readium's PDF parser
 * renders a full-size cover on open, which took 36 s and ~1.5 GB for the owner's 24 MB scanned textbook.
 */
internal class PdfReaderSession(
    private val document: PdfDocumentHandle,
    private val source: dev.kent.windbook.reader.source.SourceLease? = null,
) : PdfSession {
    override suspend fun annotationFingerprint(): String? = source?.fingerprint()
    override val format = BookFormat.PDF
    override val pageCount: Int get() = document.pageCount
    override val pages: PdfPageSource = PdfPages(document)

    override suspend fun firstPageSizes(): List<PageSize> = (0 until minOf(5, pageCount)).map { document.measure(it) }

    override fun locatorJsonFor(page: Int): String = pdfLocator(page.coerceIn(0, pageCount - 1), pageCount).toJsonString()

    override fun pageOf(locatorJson: String?): Int = pageIndexOf(locatorJson?.toLocatorOrNull(), pageCount)

    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) dev.kent.windbook.reader.closeReaderResources {
            try { kotlinx.coroutines.runBlocking { document.closeAndJoin() } } finally { source?.close() }
        }
    }
}
