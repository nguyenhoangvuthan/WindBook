package dev.kent.windbook.reader.formats

import dev.kent.windbook.core.model.BookFormat

/** Only validated registrations are readable; discovering a format does not enable it. */
class FormatCapabilities internal constructor(private val formats: Set<BookFormat>) {
    companion object { val BUILT_IN = FormatCapabilities(setOf(BookFormat.EPUB, BookFormat.PDF)) }
    fun canRead(format: BookFormat): Boolean = format in formats
}
