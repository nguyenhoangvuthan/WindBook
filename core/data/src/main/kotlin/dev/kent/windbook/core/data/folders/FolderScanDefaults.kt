package dev.kent.windbook.core.data.folders

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ScanOptions

/** Formats with working readers in the current delivery. Discovery internals still retain other IDs. */
val folderScanFormats: Set<BookFormat> = setOf(BookFormat.EPUB, BookFormat.PDF, BookFormat.TXT)

fun ScanOptions.availableFormats(): ScanOptions = copy(extensions = extensions.intersect(folderScanFormats))
