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
            services.openPublication(file).mapCatching { publication ->
                val positions = publication.positions()
                when (format) {
                    BookFormat.EPUB -> ReadiumReaderSession(format, publication, positionCount = positions.size)
                    BookFormat.PDF -> try {
                        // Locators come from Readium positions; a PDF without them can't be resumed.
                        if (positions.isEmpty()) throw ReaderException("PDF has no positions")
                        PdfReaderSession(publication, PdfDocumentHandle.open(file), positions)
                    } catch (e: Exception) {
                        publication.close()
                        throw ReaderException("PDF can't be rendered: ${e.message}")
                    }
                }
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
