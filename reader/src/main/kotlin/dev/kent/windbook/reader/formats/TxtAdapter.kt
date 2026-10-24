package dev.kent.windbook.reader.formats

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.ReadiumReaderSession
import dev.kent.windbook.reader.ReadiumServices
import dev.kent.windbook.reader.ReaderSession
import dev.kent.windbook.reader.source.SessionLease
import dev.kent.windbook.reader.source.SessionWorkspace
import dev.kent.windbook.reader.source.SourceLease
import java.io.InputStream
import java.io.PushbackReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.positions

class TxtAdapter(private val services: ReadiumServices, private val workspace: SessionWorkspace? = null) : FormatAdapter {
    override val format = BookFormat.TXT
    override suspend fun open(source: SourceLease): Result<ReaderSession> = open(source, null)
    suspend fun open(source: SourceLease, encoding: TextEncoding?): Result<ReaderSession> {
        var preparation: SessionLease? = null
        var opened: ReaderSession? = null
        try {
            return withContext(Dispatchers.IO) {
                val lease = (workspace ?: services.sourceAccess.workspace).create().also { preparation = it }
                val (document, detected) = source.openStream().use { normalizeText(it, encoding, lease, source.fingerprint()) }
                val epub = NormalizedPublicationWriter().write(document, lease)
                val publication = services.openPublication(epub).getOrThrow()
                try {
                    Result.success(ReadiumReaderSession(format, publication, publication.positions().size, source, lease, detected).also { opened = it })
                } catch (e: Throwable) { publication.close(); throw e }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { opened?.close(); preparation?.close(); throw e }
        catch (e: Exception) { preparation?.close(); return Result.failure(e) }
    }
}

/** Each bounded chunk includes its own stable paragraph IDs; only a bounded section is held in RAM. */
internal suspend fun normalizeText(input: InputStream, encoding: TextEncoding?, lease: SessionLease, identity: String): Pair<NormalizedDocument, TextEncoding> {
    var effective = TextEncoding.UTF8
    val context = currentCoroutineContext()
    val decoded = lease.writeArtifact { output ->
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            effective = decodeText(input, encoding, object : java.io.Writer() {
                var count = 0
                override fun write(chars: CharArray, offset: Int, length: Int) {
                    count += length
                    if (count >= 8192) { context.ensureActive(); count = 0 }
                    writer.write(chars, offset, length)
                }
                override fun flush() = writer.flush()
                override fun close() = flush()
            })
        }
    }
    val sections = mutableListOf<NormalizedSection>()
    val head = "<html xmlns=\"http://www.w3.org/1999/xhtml\" id=\"section\"><head><title>Text</title></head><body>"
    val tail = "</p></body></html>"
    var paragraph = 0
    var aggregate = 0L
    fun paragraphStart() = "<p id=\"p${++paragraph}\">"
    decoded.reader(Charsets.UTF_8).buffered().let { PushbackReader(it, 1) }.use { reader ->
        var end = false
        while (!end) {
            currentCoroutineContext().ensureActive()
            val text = StringBuilder(head).append(paragraphStart())
            while (text.length < 48000) {
                val char = reader.read()
                if (char < 0) { end = true; break }
                val c = char.toChar()
                when {
                    c == '\n' -> text.append("</p>").append(paragraphStart())
                    c == '&' -> text.append("&amp;")
                    c == '<' -> text.append("&lt;")
                    c == '>' -> text.append("&gt;")
                    c.isHighSurrogate() -> {
                        val low = reader.read()
                        require(low >= 0 && low.toChar().isLowSurrogate()) { "Invalid Unicode text" }
                        text.append("&#").append(Character.toCodePoint(c, low.toChar())).append(";")
                    }
                    c.isLowSurrogate() || (c < ' ' && c != '\t') || c == '\ufffe' || c == '\uffff' -> throw TextEncodingException(IllegalArgumentException("Unsupported XML text character"))
                    else -> text.append(c)
                }
            }
            text.append(tail)
            aggregate += text.length + 128 // conservative serializer declaration/markup allowance
            if (aggregate > 32L * 1024 * 1024 || sections.size >= 9996) throw TextSizeException()
            val file = lease.stage(text.toString().byteInputStream())
            sections += NormalizedSection("text/%04d.xhtml".format(java.util.Locale.ROOT, sections.size + 1), null, file)
        }
    }
    return NormalizedDocument("txt-v1:$identity:${effective.name}", "Text", sections, emptyList()) to effective
}
