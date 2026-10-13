package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Publication

class ReaderEngine(private val services: ReadiumServices) : BookOpener {

    override suspend fun open(file: File, format: BookFormat): Result<ReaderSession> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Result.failure(ReaderException("Missing file: $file"))
            services.openPublication(file).map { ReadiumReaderSession(format, it) }
        }
}

internal class ReadiumReaderSession(
    override val format: BookFormat,
    val publication: Publication,
) : ReaderSession {
    override fun close() = publication.close()
}
