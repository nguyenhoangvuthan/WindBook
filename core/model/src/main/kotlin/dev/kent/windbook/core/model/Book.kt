package dev.kent.windbook.core.model

enum class BookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
}

enum class ReadingStatus { TO_READ, READING, FINISHED }

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String,
    val fileSize: Long,
    val sha256: String,
    val coverPath: String?,
    val status: ReadingStatus,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)

/** A library entry: the book plus its reading progression in 0.0..1.0. */
data class LibraryItem(val book: Book, val progression: Double)
