package dev.kent.windbook.reader

import android.content.Context
import android.graphics.Bitmap
import kotlin.reflect.KClass
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.pdf.PdfDocument
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.resource.Resource

/**
 * Readium's PDF parser renders a cover at the first page's full device-pixel size when it opens a PDF
 * (≈ 15 500 × 25 000 px, 1.5 GB, for the owner's scanned textbook). Documents from this factory have no
 * cover, so Readium only reads metadata; WindBook renders a small cover itself (pdfCoverWebp).
 */
internal class NoCoverPdfFactory(private val inner: PdfDocumentFactory<*>) : PdfDocumentFactory<NoCoverPdfDocument> {
    override val documentType: KClass<NoCoverPdfDocument> = NoCoverPdfDocument::class

    override suspend fun open(resource: Resource, password: String?): Try<NoCoverPdfDocument, ReadError> =
        inner.open(resource, password).map { NoCoverPdfDocument(it) }
}

internal class NoCoverPdfDocument(private val inner: PdfDocument) : PdfDocument by inner {
    override suspend fun cover(context: Context): Bitmap? = null
}
