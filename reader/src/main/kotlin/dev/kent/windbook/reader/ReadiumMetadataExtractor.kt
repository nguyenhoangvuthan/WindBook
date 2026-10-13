package dev.kent.windbook.reader

import android.graphics.Bitmap
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover

class ReadiumMetadataExtractor(private val services: ReadiumServices) : MetadataExtractor {

    override suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> =
        withContext(Dispatchers.IO) {
            services.openPublication(file).mapCatching { publication ->
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title,
                        author = publication.metadata.authors.firstOrNull()?.name,
                        coverWebp = publication.cover()?.toWebp(),
                    )
                } finally {
                    publication.close()
                }
            }
        }

    private fun Bitmap.toWebp(maxHeight: Int = 600): ByteArray {
        val scaled = if (height > maxHeight) {
            val width = (width * maxHeight.toFloat() / height).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(this, width, maxHeight, true)
        } else {
            this
        }
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
            out.toByteArray()
        }
    }
}
