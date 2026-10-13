package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

class ReaderEngine(private val services: ReadiumServices) : BookOpener {

    override suspend fun open(file: File, format: BookFormat): Result<ReaderSession> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Result.failure(ReaderException("Missing file: $file"))
            services.openPublication(file).map { publication ->
                ReadiumReaderSession(format, publication, positionCount = publication.positions().size)
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
