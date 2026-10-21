package dev.kent.windbook.reader

import android.graphics.Bitmap
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover

private const val COVER_HEIGHT = 600

class ReadiumMetadataExtractor(private val services: ReadiumServices) : MetadataExtractor {

    override suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> =
        withContext(Dispatchers.IO) {
            when (format) {
                BookFormat.EPUB, BookFormat.PDF -> Unit
                BookFormat.MOBI, BookFormat.AZW3, BookFormat.FB2,
                BookFormat.CBZ, BookFormat.CBR, BookFormat.TXT ->
                    return@withContext Result.failure(ReaderException("Unsupported format: $format"))
            }
            services.openPublication(file).mapCatching { publication ->
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title,
                        author = publication.metadata.authors.firstOrNull()?.name,
                        coverWebp = if (format == BookFormat.PDF) pdfCoverWebp(file) else publication.cover()?.toWebp(),
                    )
                } finally {
                    publication.close()
                }
            }
        }
}

/** Page 1 at cover height through the framework renderer; null when the page can't be rendered. */
internal suspend fun pdfCoverWebp(file: File): ByteArray? =
    try {
        PdfDocumentHandle.open(file).use { doc ->
            val size = doc.pageSize(0)
            val width = (COVER_HEIGHT * size.width / size.height).roundToInt().coerceAtLeast(1)
            doc.render(0, CropBox.FULL, width).toWebp()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private fun Bitmap.toWebp(maxHeight: Int = COVER_HEIGHT): ByteArray {
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
