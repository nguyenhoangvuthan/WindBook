package dev.kent.windbook.reader.source

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.EpubArchiveGuard
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.reader.ReadiumMetadataExtractor
import dev.kent.windbook.reader.ReadiumServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Borrows the verified snapshot. Only the reader opener may consume its lease. */
fun interface SourceMetadata {
    suspend fun read(source: SourceLease, format: BookFormat): Result<ExtractedMetadata>
}

class ReadiumSourceMetadata(services: ReadiumServices) : SourceMetadata {
    private val extractor = ReadiumMetadataExtractor(services)
    override suspend fun read(source: SourceLease, format: BookFormat): Result<ExtractedMetadata> =
        withContext(Dispatchers.IO) {
            if (format == BookFormat.EPUB) EpubArchiveGuard().inspect(source.file)?.let {
                return@withContext Result.failure(IllegalArgumentException("Invalid EPUB: $it"))
            }
            extractor.extract(source.file, format)
        }
}
