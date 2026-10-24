package dev.kent.windbook.reader

import dev.kent.windbook.reader.formats.FormatAdapter
import dev.kent.windbook.reader.formats.FormatCapabilities
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.EpubArchiveGuard
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.PdfReaderSession
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceLease
import kotlinx.coroutines.CancellationException
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

class ReaderEngine(
    private val services: ReadiumServices,
    private val sources: SourceAccess = services.sourceAccess,
    adapters: List<FormatAdapter> = emptyList(),
) : BookOpener {
    private val adapters = adapters.associateBy { it.format }.also { registry ->
        require(registry.size == adapters.size) { "Duplicate format adapter" }
        require(registry.keys.none { it == BookFormat.EPUB || it == BookFormat.PDF }) { "Built-in readers cannot be replaced" }
    }
    override val capabilities = FormatCapabilities(setOf(BookFormat.EPUB, BookFormat.PDF) + this.adapters.keys)
    override suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession> {
        if (!capabilities.canRead(format)) return unsupported(format)
        return try {
            open(sources.acquire(source), format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Consumes the already fingerprinted lease; never opens the provider URI again. */
    override suspend fun open(source: SourceLease, format: BookFormat): Result<ReaderSession> = openConfigured(source, format, null)
    override suspend fun openText(source: SourceLease, encoding: dev.kent.windbook.reader.formats.TextEncoding?): Result<ReaderSession> = openConfigured(source, BookFormat.TXT, encoding)
    private suspend fun openConfigured(source: SourceLease, format: BookFormat, encoding: dev.kent.windbook.reader.formats.TextEncoding?): Result<ReaderSession> {
        if (!capabilities.canRead(format)) {
            source.close()
            return unsupported(format)
        }
        return openOwnedReaderSession(source) {
            when (format) {
                BookFormat.EPUB -> {
                    EpubArchiveGuard().inspect(source.file)?.let {
                        throw ReaderException("Invalid EPUB archive: $it")
                    }
                    val publication = services.openPublication(source.file).getOrThrow()
                    try {
                        ReadiumReaderSession(format, publication, publication.positions().size, source)
                    } catch (e: Throwable) { publication.close(); throw e }
                }
                BookFormat.PDF -> PdfReaderSession(PdfDocumentHandle.open(source.openDescriptor()), source)
                else -> {
                    val adapter = adapters.getValue(format)
                    if (adapter is dev.kent.windbook.reader.formats.TxtAdapter) adapter.open(source, encoding).getOrThrow()
                    else adapter.open(source).getOrThrow()
                }
            }
        }
    }

    private fun unsupported(format: BookFormat): Result<ReaderSession> =
        Result.failure(ReaderException("Unsupported format: $format"))
}

internal class ReadiumReaderSession(
    override val format: BookFormat,
    val publication: Publication,
    val positionCount: Int,
    private val source: SourceLease? = null,
    private val preparation: dev.kent.windbook.reader.source.SessionLease? = null,
    override val textEncoding: dev.kent.windbook.reader.formats.TextEncoding? = null,
) : ReaderSession {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) closeReaderResources {
            try { publication.close() } finally {
                try { preparation?.close() } finally { source?.close() }
            }
        }
    }
}
