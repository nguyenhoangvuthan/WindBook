package dev.kent.windbook.reader

import dev.kent.windbook.core.model.BookFormat
import java.io.File

/** An opened book, ready to be shown by [ReaderHostFragment]. Close it when the reader goes away. */
interface ReaderSession : AutoCloseable {
    val format: BookFormat
}

fun interface BookOpener {
    suspend fun open(file: File, format: BookFormat): Result<ReaderSession>
}

class ReaderException(message: String) : Exception(message)

/** A PDF shown by WindBook's own renderer. Pages are 0-based everywhere in the app. */
interface PdfSession : ReaderSession {
    val pageCount: Int
    val pages: dev.kent.windbook.reader.pdf.PdfPageSource
    suspend fun firstPageSizes(): List<dev.kent.windbook.core.model.PageSize>
    fun locatorJsonFor(page: Int): String
    fun pageOf(locatorJson: String?): Int
}
