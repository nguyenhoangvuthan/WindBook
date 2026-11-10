package dev.kent.windbook.reader

import android.graphics.Bitmap
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.renderedHeight
import dev.kent.windbook.reader.metadata.CoverEncoder
import dev.kent.windbook.reader.metadata.EpubMetadataFastPath
import dev.kent.windbook.reader.metadata.FastEpubMetadata
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.publication.services.positions
import dev.kent.windbook.reader.metadata.MetadataWorkCoordinator
import dev.kent.windbook.reader.metadata.pdfContentsFromLinks

private const val COVER_HEIGHT = 600

class ReadiumMetadataExtractor(private val services: ReadiumServices, private val work: MetadataWorkCoordinator = MetadataWorkCoordinator.shared) : MetadataExtractor {

    override suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> =
        work.run { withContext(Dispatchers.IO) {
            when (format) {
                BookFormat.EPUB, BookFormat.PDF -> Unit
                BookFormat.MOBI, BookFormat.AZW3, BookFormat.FB2,
                BookFormat.CBZ, BookFormat.CBR, BookFormat.TXT ->
                    return@withContext Result.failure(ReaderException("Unsupported format: $format"))
            }
            if (format == BookFormat.EPUB) when (val fast = EpubMetadataFastPath().read(file)) {
                is FastEpubMetadata.Success -> return@withContext Result.success(fast.metadata)
                is FastEpubMetadata.Invalid -> return@withContext Result.failure(fast.error)
                FastEpubMetadata.Unsupported -> Unit
            }
            services.openPublication(file).mapCatching { publication ->
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title,
                        author = publication.metadata.authors.firstOrNull()?.name,
                        pdfContents = if (format == BookFormat.PDF) pdfContentsFromLinks(publication.tableOfContents, publication.metadata.numberOfPages ?: publication.positions().size, publication.readingOrder.firstOrNull()?.href.toString().substringBefore('#')) else null,
                        coverWebp = if (format == BookFormat.PDF) pdfCoverWebp(file) else publication.cover()?.let { bitmap ->
                            try { CoverEncoder.encode(bitmap) } finally { bitmap.recycle() }
                        },
                    )
                } finally {
                    publication.close()
                }
            }
        } }
}

/** Page 1 through the framework renderer; optional when no safe cover can be rendered. */
internal suspend fun pdfCoverWebp(file: File): ByteArray? =
    try {
        pdfCoverBitmap(file)?.let { bitmap ->
            try { CoverEncoder.encode(bitmap) } finally { bitmap.recycle() }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

/** Bounds the actual render target, before allocation; extreme portraits may have no safe width. */
internal suspend fun pdfCoverBitmap(file: File): Bitmap? =
    PdfDocumentHandle.open(file).use { doc ->
        val size = doc.pageSize(0)
        val width = (COVER_HEIGHT * size.width / maxOf(size.width, size.height)).toInt()
        if (width !in 1..COVER_HEIGHT || renderedHeight(size, CropBox.FULL, width) !in 1..COVER_HEIGHT) {
            null // At least one pixel wide would exceed the height bound; omit the optional cover.
        } else {
            doc.render(0, CropBox.FULL, width)
        }
    }
