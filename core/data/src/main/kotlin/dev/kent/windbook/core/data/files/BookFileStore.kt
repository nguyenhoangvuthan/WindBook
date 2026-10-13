package dev.kent.windbook.core.data.files

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.FormatSniffer
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class FileTooLargeException(val limitBytes: Long) : IOException("File exceeds $limitBytes bytes")

data class CopiedFile(val file: File, val size: Long, val sha256: String)

/** Owns the app-private copies of books and covers under [root]. */
class BookFileStore(private val root: File) {
    val booksDir = File(root, "books")
    val coversDir = File(root, "covers")
    val tempDir = File(root, "import-tmp")

    /** Streams [input] into a temp file while hashing; never holds the whole book in memory. */
    fun copyToTemp(input: InputStream, maxBytes: Long): CopiedFile {
        tempDir.mkdirs()
        val temp = File.createTempFile("import-", ".part", tempDir)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        try {
            temp.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxBytes) throw FileTooLargeException(maxBytes)
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
        return CopiedFile(temp, total, digest.digest().joinToString("") { "%02x".format(it) })
    }

    fun readHeader(file: File): ByteArray = file.inputStream().use { input ->
        val buffer = ByteArray(FormatSniffer.HEADER_SIZE)
        var filled = 0
        while (filled < buffer.size) {
            val read = input.read(buffer, filled, buffer.size - filled)
            if (read < 0) break
            filled += read
        }
        buffer.copyOf(filled)
    }

    fun commit(temp: File, id: String, format: BookFormat): File {
        booksDir.mkdirs()
        val destination = File(booksDir, "$id.${format.extension}")
        if (!temp.renameTo(destination)) {
            temp.copyTo(destination, overwrite = true)
            temp.delete()
        }
        return destination
    }

    fun writeCover(id: String, webp: ByteArray): File {
        coversDir.mkdirs()
        return File(coversDir, "$id.webp").apply { writeBytes(webp) }
    }

    fun delete(path: String?) {
        if (path != null) File(path).delete()
    }

    fun usableBytes(): Long = root.usableSpace
}
