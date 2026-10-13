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
