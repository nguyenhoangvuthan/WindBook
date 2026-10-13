package dev.kent.windbook.core.model

enum class FileSignature { PDF, ZIP, UNKNOWN }

/** Detects a file type from its first bytes, ignoring the file name. */
object FormatSniffer {
    const val HEADER_SIZE = 1024

    private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)
    private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    fun signature(header: ByteArray): FileSignature = when {
        header.startsWith(ZIP_MAGIC) -> FileSignature.ZIP
        // The PDF spec tolerates bytes before the header, within the first 1024 bytes.
        header.indexOf(PDF_MAGIC) >= 0 -> FileSignature.PDF
        else -> FileSignature.UNKNOWN
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.indexOf(needle: ByteArray): Int {
        if (needle.size > size) return -1
        outer@ for (i in 0..size - needle.size) {
            for (j in needle.indices) if (this[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
