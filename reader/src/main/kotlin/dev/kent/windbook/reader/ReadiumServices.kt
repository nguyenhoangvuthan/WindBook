package dev.kent.windbook.reader

import android.content.Context
import android.net.Uri
import org.readium.r2.shared.util.content.ContentResource
import dev.kent.windbook.reader.source.SourceAccess
import java.io.File
import org.readium.adapter.pdfium.document.PdfiumDocumentFactory
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.Asset
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
    internal val sourceAccess by lazy { SourceAccess(context) }
    private val resolver = context.contentResolver

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

    suspend fun openPublication(uri: Uri): Result<Publication> {
        val resource = ContentResource(uri, resolver)
        val asset = try {
            assetRetriever.retrieve(resource).getOrElse {
                resource.close()
                return Result.failure(ReaderException(it.message))
            }
        } catch (e: Throwable) {
            resource.close()
            throw e
        }
        return openAsset(asset)
    }

    suspend fun openPublication(file: File): Result<Publication> {
        val asset = assetRetriever.retrieve(file.toUrl(isDirectory = false))
            .getOrElse { return Result.failure(ReaderException(it.message)) }
        return openAsset(asset)
    }

    private suspend fun openAsset(asset: Asset): Result<Publication> {
        return try {
            val publication = publicationOpener.open(asset, allowUserInteraction = false)
                .getOrElse {
                    asset.close()
                    return Result.failure(ReaderException(it.message))
                }
            Result.success(publication)
        } catch (e: Throwable) {
            asset.close()
            throw e
        }
    }
}
