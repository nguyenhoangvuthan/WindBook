package dev.kent.windbook.core.model

import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

enum class EpubArchiveError {
    NOT_AN_EPUB, CORRUPT, TOO_LARGE_UNCOMPRESSED, TOO_MANY_ENTRIES, UNSAFE_PATHS,
}

/** Rejects EPUB zips that are malformed, malicious (path traversal) or zip bombs. */
class EpubArchiveGuard(
    private val maxEntries: Int = 10_000,
    private val maxUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
) {
    fun inspect(file: File): EpubArchiveError? {
        try {
            ZipFile(file).use { zip ->
                var count = 0
                var total = 0L
                var hasContainer = false
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    count++
                    if (count > maxEntries) return EpubArchiveError.TOO_MANY_ENTRIES
                    if (isUnsafe(entry.name)) return EpubArchiveError.UNSAFE_PATHS
                    if (entry.size > 0) total += entry.size
                    if (total > maxUncompressedBytes) return EpubArchiveError.TOO_LARGE_UNCOMPRESSED
                    if (entry.name == "META-INF/container.xml") hasContainer = true
                }
                return if (hasContainer) null else EpubArchiveError.NOT_AN_EPUB
            }
        } catch (e: IOException) {
            // ZipException is an IOException; Android 14+ also throws it for unsafe names.
            return if (e.message?.contains("path", ignoreCase = true) == true) EpubArchiveError.UNSAFE_PATHS else EpubArchiveError.CORRUPT
        }
    }

    private fun isUnsafe(name: String): Boolean {
        val normalized = name.replace('\\', '/')
        return normalized.startsWith("/") || normalized.split('/').any { it == ".." }
    }
}
