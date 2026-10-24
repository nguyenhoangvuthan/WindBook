package dev.kent.windbook.reader.formats

import java.io.StringWriter
import org.junit.Assert.*
import org.junit.Test

class TxtAdapterTest {
    private fun decode(bytes: ByteArray, encoding: TextEncoding? = null): Pair<String, TextEncoding> {
        val output = StringWriter()
        val detected = decodeText(bytes.inputStream(), encoding, output)
        return output.toString() to detected
    }
    @Test fun strictUnicodeAndNewlines() {
        val text = "Tiếng Việt\nDòng hai"
        assertEquals(text, decode(text.replace("\n", "\r\n").toByteArray()).first)
        assertEquals(text, decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)).first)
        assertEquals(TextEncoding.UTF16_LE, decode(byteArrayOf(0xff.toByte(), 0xfe.toByte()) + text.toByteArray(Charsets.UTF_16LE)).second)
        assertEquals(text, decode(byteArrayOf(0xfe.toByte(), 0xff.toByte()) + text.toByteArray(Charsets.UTF_16BE)).first)
    }
    @Test fun invalidUtf8RequiresExplicitChoice() {
        val failure = assertThrows(TextEncodingException::class.java) { decode(byteArrayOf(0xc0.toByte())) }
        assertEquals("ENCODING_REQUIRED", failure.errorCode)
        assertEquals("À", decode(byteArrayOf(0xc0.toByte()), TextEncoding.WINDOWS_1258).first)
        val vietnamese = "Tiê\u0301ng Viê\u0323t"
        assertEquals(vietnamese, decode(vietnamese.toByteArray(TextEncoding.WINDOWS_1258.charset), TextEncoding.WINDOWS_1258).first)
        assertEquals(TextEncoding.WINDOWS_1258, decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte(), 0xc0.toByte()), TextEncoding.WINDOWS_1258).second)
    }
    @Test fun bomlessUtf16WithXmlControlsRequiresEncodingChoice() = kotlinx.coroutines.runBlocking {
        val root = java.nio.file.Files.createTempDirectory("txt-controls").toFile()
        val lease = dev.kent.windbook.reader.source.SessionWorkspace(root).create()
        try {
            assertThrows(TextEncodingException::class.java) { kotlinx.coroutines.runBlocking {
                normalizeText("Plain text".toByteArray(Charsets.UTF_16LE).inputStream(), null, lease, "id")
            } }
        } finally { lease.close(); root.deleteRecursively() }
        Unit
    }
    @Test fun normalizedContentIsEscapedBoundedAndDeterministic() = kotlinx.coroutines.runBlocking {
        val root = java.nio.file.Files.createTempDirectory("txt-test").toFile()
        try {
            suspend fun build(): Pair<List<String>, List<String>> {
                val lease = dev.kent.windbook.reader.source.SessionWorkspace(java.io.File(root, java.util.UUID.randomUUID().toString())).create()
                try {
                    val source = java.io.File(root, "book.txt").apply { writeText("<script>\n" + "&😀".repeat(40000)) }
                    val doc = normalizeText(source.inputStream(), null, lease, "id")
                    val epub = NormalizedPublicationWriter().write(doc.first, lease)
                    return java.util.zip.ZipFile(epub).use { zip ->
                        val names = zip.entries().toList().map { it.name }.filter { it.startsWith("EPUB/text/") }
                        names to names.map { zip.getInputStream(zip.getEntry(it)).bufferedReader().readText() }
                    }
                } finally { lease.close() }
            }
            val first = build()
            assertEquals(first, build())
            assertEquals("EPUB/text/0001.xhtml", first.first.first())
            assertTrue(first.second.first().contains("&lt;script&gt;"))
            assertTrue(first.second.all { it.length <= 65536 })
            assertTrue(first.second.size > 1)
        } finally { root.deleteRecursively() }
    }
    @Test fun boundariesPreserveCrAndSurrogates() {
        val value = "x".repeat(8191) + "\r\n😀\rZ"
        assertEquals("x".repeat(8191) + "\n😀\nZ", decode(value.toByteArray()).first)
    }
}
