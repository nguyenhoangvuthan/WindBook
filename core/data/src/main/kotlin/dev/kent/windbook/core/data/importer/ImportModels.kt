package dev.kent.windbook.core.data.importer

import java.io.InputStream

enum class ImportError {
    UNSUPPORTED_FORMAT,
    NOT_AN_EPUB,
    CORRUPT,
    TOO_LARGE,
    TOO_LARGE_UNCOMPRESSED,
    TOO_MANY_ENTRIES,
    UNSAFE_PATHS,
    NO_SPACE,
    READ_FAILED,
    UNREADABLE,
}

/** Where a book comes from (a SAF Uri in the app, a File in tests). */
interface ImportSource {
    val displayName: String?
    val sourceUri: String?
    fun open(): InputStream
}

sealed interface ImportResult {
    data class Imported(val bookId: String) : ImportResult
    data class Duplicate(val bookId: String) : ImportResult
    data class Failed(val error: ImportError) : ImportResult
}
