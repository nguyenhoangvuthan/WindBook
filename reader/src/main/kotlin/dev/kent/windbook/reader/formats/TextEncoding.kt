package dev.kent.windbook.reader.formats

import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackInputStream
import java.io.Writer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

enum class TextEncoding(val charset: Charset) {
    UTF8(Charsets.UTF_8), UTF16_LE(Charsets.UTF_16LE), UTF16_BE(Charsets.UTF_16BE),
    WINDOWS_1258(Charset.forName("windows-1258")),
}
class TextEncodingException(cause: Throwable) : Exception("ENCODING_REQUIRED", cause) {
    val errorCode = "ENCODING_REQUIRED"
}
class TextSizeException : Exception("TEXT_TOO_LARGE")

/** Explicit choice wins; otherwise BOM detection precedes strict UTF-8. Neither stream is closed. */
fun decodeText(input: InputStream, override: TextEncoding?, output: Writer): TextEncoding {
    val bytes = PushbackInputStream(input, 3)
    val prefix = ByteArray(3)
    var count = 0
    while (count < 3) { val n = bytes.read(prefix, count, 3 - count); if (n < 0) break; if (n > 0) count += n }
    val bom = when {
        count >= 3 && prefix[0] == 0xef.toByte() && prefix[1] == 0xbb.toByte() && prefix[2] == 0xbf.toByte() -> TextEncoding.UTF8 to 3
        count >= 2 && prefix[0] == 0xff.toByte() && prefix[1] == 0xfe.toByte() -> TextEncoding.UTF16_LE to 2
        count >= 2 && prefix[0] == 0xfe.toByte() && prefix[1] == 0xff.toByte() -> TextEncoding.UTF16_BE to 2
        else -> null
    }
    val skip = bom?.second ?: 0
    bytes.unread(prefix, skip, count - skip)
    val encoding = override ?: bom?.first ?: TextEncoding.UTF8
    val reader = InputStreamReader(bytes, encoding.charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))
    var previousCr = false
    var units = 0L
    val buffer = CharArray(8192)
    try {
        while (true) {
            val read = reader.read(buffer)
            if (read < 0) break
            for (index in 0 until read) {
                val char = buffer[index]
                if (char == '\n' && previousCr) { previousCr = false; continue }
                previousCr = char == '\r'
                if (++units > 32L * 1024 * 1024) throw TextSizeException()
                output.write(if (previousCr) '\n'.code else char.code)
            }
        }
    } catch (e: CharacterCodingException) { throw TextEncodingException(e) }
    return encoding
}
