package dev.kent.windbook.core.model

enum class BookFormat(val extension: String, val mimeType: String) {
    EPUB("epub", "application/epub+zip"),
    PDF("pdf", "application/pdf"),
    MOBI("mobi", "application/x-mobipocket-ebook"),
    AZW3("azw3", "application/vnd.amazon.mobi8-ebook"),
    FB2("fb2", "application/x-fictionbook+xml"),
    CBZ("cbz", "application/vnd.comicbook+zip"),
    CBR("cbr", "application/vnd.comicbook-rar"),
    TXT("txt", "text/plain"),
}

enum class ReadingStatus { TO_READ, READING, FINISHED }

data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val format: BookFormat,
    val filePath: String?,
    val fileSize: Long,
    val sha256: String?,
    val coverPath: String?,
    val status: ReadingStatus,
    val addedAt: Long,
    val lastOpenedAt: Long?,
)

/** A library entry: the book plus its reading progression in 0.0..1.0. */
data class LibraryItem(val book: Book, val progression: Double)
