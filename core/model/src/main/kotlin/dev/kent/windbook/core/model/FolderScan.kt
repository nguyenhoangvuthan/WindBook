package dev.kent.windbook.core.model

import java.util.Locale

data class ScanOptions(
    val extensions: Set<BookFormat> = setOf(BookFormat.EPUB, BookFormat.PDF),
    val recursive: Boolean = true,
)

/** Discovery uses only the final suffix; opening still validates the actual file. */
fun candidateFormat(name: String): BookFormat? {
    val extension = name.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
    return BookFormat.entries.firstOrNull { it.extension == extension }
}

fun ScanOptions.accepts(name: String): Boolean = candidateFormat(name) in extensions
