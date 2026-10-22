package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.EpubArchiveGuard
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.PdfReaderSession
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

class ReaderEngine(
    private val services: ReadiumServices,
    private val sources: SourceAccess = services.sourceAccess,
) : BookOpener {
    override suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession> {
        if (format != BookFormat.EPUB && format != BookFormat.PDF) return unsupported(format)
        return try {
            open(sources.acquire(source), format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Consumes the already fingerprinted lease; never opens the provider URI again. */
    override suspend fun open(source: SourceLease, format: BookFormat): Result<ReaderSession> {
        var session: ReaderSession? = null
        try {
            return withContext(Dispatchers.IO) {
                session = when (format) {
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
                    else -> return@withContext unsupported(format)
                }
                currentCoroutineContext().ensureActive()
                Result.success(session)
            }.also { if (it.isFailure) source.close() }
        } catch (e: Throwable) {
            session?.close()
            source.close()
            if (e is CancellationException) throw e
            if (e is Error) throw e
            return Result.failure(e)
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
) : ReaderSession {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    override fun close() {
        if (closed.compareAndSet(false, true)) closeReaderResources {
            try { publication.close() } finally { source?.close() }
        }
    }
}
