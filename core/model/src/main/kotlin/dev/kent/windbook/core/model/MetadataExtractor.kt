package dev.kent.windbook.core.model

import java.io.File

/** Metadata pulled from a book file. [coverWebp] is an already-encoded WebP image. */
class ExtractedMetadata(
    val title: String?,
    val author: String?,
    val coverWebp: ByteArray?,
)

/** Port implemented by the reader engine; a failure means the file can't be opened. */
fun interface MetadataExtractor {
    suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata>
}
