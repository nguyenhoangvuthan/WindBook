package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.PdfReaderSession
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

class ReaderEngine(private val services: ReadiumServices) : BookOpener {

    override suspend fun open(file: File, format: BookFormat): Result<ReaderSession> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Result.failure(ReaderException("Missing file: $file"))
            when (format) {
                BookFormat.EPUB -> services.openPublication(file).map { publication ->
                    ReadiumReaderSession(format, publication, positionCount = publication.positions().size)
                }
                // Not through Readium: its PDF parser renders a full-size cover on open (see PdfReaderSession).
                BookFormat.PDF -> runCatching { PdfReaderSession(PdfDocumentHandle.open(file)) }
                    .recoverCatching { throw ReaderException("PDF can't be rendered: ${it.message}") }
            }
        }
}

internal class ReadiumReaderSession(
    override val format: BookFormat,
    val publication: Publication,
    val positionCount: Int,
) : ReaderSession {
    override fun close() = publication.close()
}
