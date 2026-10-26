package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.reader.source.SourceLease

/** An opened book, ready to be shown by [ReaderHostFragment]. Close it when the reader goes away. */
enum class NavigationSurface { PUBLICATION, PDF, IMAGES }

interface ReaderSession : AutoCloseable {
    val surface: NavigationSurface get() = NavigationSurface.PUBLICATION
    val format: BookFormat
    val textEncoding: dev.kent.windbook.reader.formats.TextEncoding? get() = null
}

fun interface BookOpener {
    val capabilities: dev.kent.windbook.reader.formats.FormatCapabilities get() = dev.kent.windbook.reader.formats.FormatCapabilities.BUILT_IN
    suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession>
    suspend fun openText(source: SourceLease, encoding: dev.kent.windbook.reader.formats.TextEncoding?): Result<ReaderSession> = open(source, BookFormat.TXT)
    suspend fun open(file: File, format: BookFormat): Result<ReaderSession> = open(BookSource.Owned(file.path), format)
    /** Transfers ownership, even on failure. Implementations supporting verified sources override this. */
    suspend fun open(source: SourceLease, format: BookFormat): Result<ReaderSession> {
        source.close()
        return Result.failure(ReaderException("Acquired source opening is unsupported"))
    }
}

class ReaderException(message: String) : Exception(message)

/** A PDF shown by WindBook's own renderer. Pages are 0-based everywhere in the app. */
interface PdfSession : ReaderSession {
    override val surface: NavigationSurface get() = NavigationSurface.PDF
    suspend fun annotationFingerprint(): String? = null
    val pageCount: Int
    val pages: dev.kent.windbook.reader.pdf.PdfPageSource
    suspend fun firstPageSizes(): List<dev.kent.windbook.core.model.PageSize>
    fun locatorJsonFor(page: Int): String
    fun pageOf(locatorJson: String?): Int
}
