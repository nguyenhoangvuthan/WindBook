package dev.kent.windbook.reader.metadata

import android.graphics.ImageDecoder
import android.util.Xml
import dev.kent.windbook.core.model.EpubArchiveGuard
import dev.kent.windbook.core.model.ExtractedMetadata
import java.io.File
import java.io.IOException
import java.io.StringReader
import java.net.URI
import java.nio.ByteBuffer
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.xmlpull.v1.XmlPullParser

internal sealed interface FastEpubMetadata {
    data class Success(val metadata: ExtractedMetadata) : FastEpubMetadata
    data object Unsupported : FastEpubMetadata
    data class Invalid(val error: Exception) : FastEpubMetadata
}

/** A narrow optimization; unsupported EPUB structures retain Readium's parser. */
internal class EpubMetadataFastPath(private val onStage: ((String, Long) -> Unit)? = null) {
    suspend fun read(file: File): FastEpubMetadata {
        val owner = currentCoroutineContext()
        var checkpoint = if (onStage != null) System.nanoTime() else 0L
        fun recorded(stage: String) {
            if (onStage != null) {
                val now = System.nanoTime()
                onStage.invoke(stage, now - checkpoint)
                checkpoint = now
            }
        }
        owner.ensureActive()
        EpubArchiveGuard().inspect(file)?.let {
            return FastEpubMetadata.Invalid(IOException("Invalid EPUB archive: $it"))
        }
        recorded("guard")
        try {
            return ZipFile(file).use { zip ->
                val names = HashSet<String>()
                var mayContainRaster = false
                zip.entries().asSequence().forEach {
                    owner.ensureActive()
                    if (!names.add(it.name)) throw UnsupportedEpub()
                    if (!it.isDirectory && it.name != "mimetype" &&
                        it.name.substringAfterLast('.', "").lowercase(java.util.Locale.ROOT) !in NON_RASTER_EXTENSIONS) {
                        mayContainRaster = true
                    }
                }
                // This changes only parser routing: Readium still decides all text-only/no-cover metadata.
                if (!mayContainRaster) return@use FastEpubMetadata.Unsupported
                fun bytes(name: String, limit: Int): ByteArray {
                    owner.ensureActive()
                    val entry = zip.getEntry(name) ?: throw UnsupportedEpub()
                    if (entry.isDirectory || entry.size > limit) throw UnsupportedEpub()
                    return zip.getInputStream(entry).use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var size = 0
                        while (true) {
                            owner.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            size += read
                            if (size > limit) throw UnsupportedEpub()
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray()
                    }
                }
                if (bytes("mimetype", 128).toString(Charsets.UTF_8) != "application/epub+zip") throw UnsupportedEpub()
                val roots = ArrayList<String>()
                parse(bytes("META-INF/container.xml", XML_LIMIT)) { parser, parent ->
                    if (parser.depth == 1 && (parser.namespace != CONTAINER || parser.name != "container")) throw UnsupportedEpub()
                    if (parser.namespace == CONTAINER && parser.name == "rootfile") {
                        if (parser.depth != 3 || parent != (CONTAINER to "rootfiles")) throw UnsupportedEpub()
                        if (parser.attr("media-type") != "application/oebps-package+xml") throw UnsupportedEpub()
                        roots.add(parser.attr("full-path") ?: throw UnsupportedEpub())
                    }
                }
                if (roots.size != 1) throw UnsupportedEpub()
                val opf = path("", roots.single())
                val titles = ArrayList<String>()
                val authors = ArrayList<String>()
                val declaredCovers = ArrayList<String>()
                val manifest = LinkedHashMap<String, Item>()
                var metadataDepth = -1
                var manifestDepth = -1
                var packageSeen = false
                var spineSeen = false
                val spineIds = ArrayList<String>()
                parse(bytes(opf, XML_LIMIT)) { parser, parent ->
                    val inMetadata = parent == (OPF to "metadata") && parser.depth == metadataDepth + 1
                    when {
                        parser.namespace == OPF && parser.name == "package" -> {
                            if (packageSeen || parser.depth != 1 || parser.attr("version")?.let { it == "2.0" || it.startsWith("3.") } != true) throw UnsupportedEpub()
                            packageSeen = true
                        }
                        parser.namespace == OPF && parser.name == "metadata" -> {
                            if (parser.depth != 2 || metadataDepth != -1) throw UnsupportedEpub()
                            metadataDepth = parser.depth
                        }
                        parser.namespace == OPF && parser.name == "manifest" -> {
                            if (parser.depth != 2 || manifestDepth != -1) throw UnsupportedEpub()
                            manifestDepth = parser.depth
                        }
                        parser.namespace == OPF && parser.name == "spine" -> {
                            if (parser.depth != 2 || spineSeen) throw UnsupportedEpub()
                            spineSeen = true
                        }
                        parser.namespace == OPF && parser.name == "itemref" -> {
                            if (!spineSeen || parser.depth != 3 || parent != (OPF to "spine")) throw UnsupportedEpub()
                            spineIds.add(parser.attr("idref") ?: throw UnsupportedEpub())
                        }
                        parser.namespace == DC && inMetadata && parser.name == "title" -> titles.add(plainText(parser))
                        parser.namespace == DC && inMetadata && parser.name == "creator" -> {
                            val role = parser.getAttributeValue(OPF, "role")
                            if (role != null && role != "aut") throw UnsupportedEpub()
                            authors.add(plainText(parser))
                        }
                        parser.namespace == DC && inMetadata && parser.name == "contributor" -> throw UnsupportedEpub()
                        parser.namespace == OPF && parser.name == "meta" && inMetadata -> {
                            if (parser.attr("refines") != null) throw UnsupportedEpub()
                            // Readium can prefer global Dublin Core/RDFa metadata over dc elements.
                            val identityProperties = setOf("title", "creator", "contributor")
                            val properties = listOfNotNull(parser.attr("property"), parser.attr("name"))
                            if (properties.any { value -> value.split(Regex("\\s+")).any {
                                    it.substringAfterLast('/').substringAfterLast(':') in identityProperties
                                } }) throw UnsupportedEpub()
                            if (parser.attr("name") == "cover") declaredCovers.add(parser.attr("content") ?: throw UnsupportedEpub())
                        }
                        parser.namespace == OPF && parser.name == "item" && parser.depth == manifestDepth + 1 && parent == (OPF to "manifest") -> {
                            val id = parser.attr("id") ?: throw UnsupportedEpub()
                            val item = Item(parser.attr("href") ?: throw UnsupportedEpub(), parser.attr("media-type"),
                                parser.attr("properties")?.split(Regex("\\s+"))?.contains("cover-image") == true)
                            if (manifest.put(id, item) != null) throw UnsupportedEpub()
                        }
                    }
                }
                recorded("xml")
                if (!packageSeen || metadataDepth != 2 || manifestDepth != 2 || !spineSeen ||
                    spineIds.isEmpty() || spineIds.any { it !in manifest } ||
                    titles.size != 1 || authors.size > 1 || declaredCovers.size > 1) throw UnsupportedEpub()
                val title = titles.single().takeIf { it.isNotBlank() } ?: throw UnsupportedEpub()
                val coverIds = manifest.filterValues { it.cover }.keys + declaredCovers
                if (coverIds.size != 1) throw UnsupportedEpub()
                val cover = manifest[coverIds.single()] ?: throw UnsupportedEpub()
                if (cover.media !in setOf("image/png", "image/jpeg")) throw UnsupportedEpub()
                val encoded = bytes(path(opf.substringBeforeLast('/', ""), cover.href), COVER_LIMIT)
                recorded("coverRead")
                owner.ensureActive()
                val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    if (width <= 0 || height <= 0) throw UnsupportedEpub()
                    val scale = minOf(1.0, 600.0 / maxOf(width, height))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.setTargetSize(maxOf(1, (width * scale).toInt()), maxOf(1, (height * scale).toInt()))
                }
                try {
                    recorded("decode")
                    owner.ensureActive()
                    val webp = CoverEncoder.encode(bitmap)
                    recorded("compress")
                    owner.ensureActive()
                    FastEpubMetadata.Success(ExtractedMetadata(title, authors.singleOrNull()?.takeIf { it.isNotBlank() }, webp))
                } finally { bitmap.recycle() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (unsafe: UnsafePath) { return FastEpubMetadata.Invalid(unsafe) }
        catch (_: Exception) { owner.ensureActive(); return FastEpubMetadata.Unsupported }
    }

    private data class Item(val href: String, val media: String?, val cover: Boolean)
    private class UnsupportedEpub : IOException()
    private class UnsafePath : IOException("EPUB resource escapes archive")

    private fun path(base: String, reference: String): String {
        val uri = URI(reference)
        if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null) throw UnsupportedEpub()
        val decoded = uri.path ?: throw UnsupportedEpub()
        if (decoded.startsWith('/') || '\\' in decoded || '\u0000' in decoded) throw UnsafePath()
        val segments = base.split('/').filter { it.isNotEmpty() }.toMutableList()
        for (segment in decoded.split('/')) when (segment) {
            "", "." -> Unit
            ".." -> if (segments.isEmpty()) throw UnsafePath() else segments.removeAt(segments.lastIndex)
            else -> segments.add(segment)
        }
        return segments.joinToString("/").takeIf { it.isNotBlank() } ?: throw UnsupportedEpub()
    }

    private fun parse(xml: ByteArray, onStart: (XmlPullParser, Pair<String, String>?) -> Unit) {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml.toString(Charsets.UTF_8)))
        val parents = ArrayList<Pair<String, String>>()
        while (true) when (parser.nextToken()) {
            XmlPullParser.END_DOCUMENT -> return
            XmlPullParser.DOCDECL -> throw UnsupportedEpub()
            XmlPullParser.START_TAG -> {
                if (parser.depth > 64) throw UnsupportedEpub()
                while (parents.size >= parser.depth) parents.removeAt(parents.lastIndex)
                val element = parser.namespace to parser.name
                onStart(parser, parents.lastOrNull())
                parents.add(element)
            }
        }
    }

    private fun plainText(parser: XmlPullParser): String {
        val text = StringBuilder()
        while (true) when (parser.nextToken()) {
            XmlPullParser.END_TAG -> return text.toString().trim().replace(Regex("\\s+"), " ")
            XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                text.append(parser.text ?: throw UnsupportedEpub())
                if (text.length > 65_536) throw UnsupportedEpub()
            }
            XmlPullParser.START_TAG, XmlPullParser.DOCDECL, XmlPullParser.END_DOCUMENT -> throw UnsupportedEpub()
        }
    }

    private fun XmlPullParser.attr(name: String): String? = getAttributeValue(null, name)

    companion object {
        private const val XML_LIMIT = 1_048_576
        private const val COVER_LIMIT = 16 * 1_048_576
        private const val OPF = "http://www.idpf.org/2007/opf"
        private const val DC = "http://purl.org/dc/elements/1.1/"
        private const val CONTAINER = "urn:oasis:names:tc:opendocument:xmlns:container"
        private val NON_RASTER_EXTENSIONS = setOf("xml", "opf", "xhtml", "html", "htm", "ncx", "css", "js",
            "txt", "json", "smil", "otf", "ttf", "woff", "woff2", "svg", "gif", "webp", "avif", "bmp", "tiff", "tif")
    }
}
