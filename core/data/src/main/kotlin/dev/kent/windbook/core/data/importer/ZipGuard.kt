package dev.kent.windbook.core.data.importer

import dev.kent.windbook.core.model.EpubArchiveError
import dev.kent.windbook.core.model.EpubArchiveGuard
import java.io.File

/** Import's existing error contract over the guard shared with folder reading. */
class ZipGuard(
    maxEntries: Int = 10_000,
    maxUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
) {
    private val guard = EpubArchiveGuard(maxEntries, maxUncompressedBytes)
    fun inspectEpub(file: File): ImportError? = when (guard.inspect(file)) {
        null -> null
        EpubArchiveError.NOT_AN_EPUB -> ImportError.NOT_AN_EPUB
        EpubArchiveError.CORRUPT -> ImportError.CORRUPT
        EpubArchiveError.TOO_LARGE_UNCOMPRESSED -> ImportError.TOO_LARGE_UNCOMPRESSED
        EpubArchiveError.TOO_MANY_ENTRIES -> ImportError.TOO_MANY_ENTRIES
        EpubArchiveError.UNSAFE_PATHS -> ImportError.UNSAFE_PATHS
    }
}
