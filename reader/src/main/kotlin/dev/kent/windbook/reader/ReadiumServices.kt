package dev.kent.windbook.reader

import android.content.Context
import java.io.File
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

/** Shared Readium objects (one per app). */
class ReadiumServices(
    context: Context,
    pdfFactory: PdfDocumentFactory<*>? = PdfiumDocumentFactory(context),
) {
    // Never DefaultHttpClient: see OfflineHttpClient.
    private val httpClient: HttpClient = OfflineHttpClient

    private val assetRetriever = AssetRetriever(context.contentResolver, httpClient)

    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context,
            assetRetriever = assetRetriever,
            httpClient = httpClient,
            pdfFactory = pdfFactory?.let(::NoCoverPdfFactory),
        ),
    )

    suspend fun openPublication(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false))
            .getOrElse { return Result.failure(ReaderException(it.message)) }
        val publication = publicationOpener.open(asset, allowUserInteraction = false)
            .getOrElse {
                asset.close()
                return Result.failure(ReaderException(it.message))
            }
        return Result.success(publication)
    }
}
