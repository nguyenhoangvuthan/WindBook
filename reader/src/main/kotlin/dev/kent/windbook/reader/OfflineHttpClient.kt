package dev.kent.windbook.reader

import java.io.IOException
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.http.HttpClient
import org.readium.r2.shared.util.http.HttpError
import org.readium.r2.shared.util.http.HttpRequest
import org.readium.r2.shared.util.http.HttpStreamResponse

/**
 * WindBook never goes online (it has no INTERNET permission). Readium fetches EPUB resources that are missing
 * from the book over HTTP, and the denied DNS lookup throws SecurityException, which crashes the app.
 * Failing here instead makes the navigator treat the file as missing, as a browser would.
 */
internal object OfflineHttpClient : HttpClient {
    override suspend fun stream(request: HttpRequest): Try<HttpStreamResponse, HttpError> =
        Try.failure(HttpError.IO(IOException("WindBook is offline: ${request.url}")))
}
