package dev.kent.windbook.reader.formats

import dev.kent.windbook.reader.source.SessionLease
import java.io.File
import java.io.StringReader
import java.net.URI
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Inputs are staged files, never whole-book arrays. Paths are stable publication-relative names. */
data class NormalizedDocument(val id: String, val title: String, val sections: List<NormalizedSection>, val resources: List<NormalizedResource>)
data class NormalizedSection(val href: String, val title: String?, val xhtmlFile: File)
data class NormalizedResource(val href: String, val mediaType: String, val file: File)

class NormalizedPublicationWriter {
    suspend fun write(document: NormalizedDocument, lease: SessionLease): File {
        require(document.sections.isNotEmpty())
        require(document.id.length <= 4096 && document.title.length <= 4096 && document.sections.all { (it.title?.length ?: 0) <= 4096 })
        require(document.sections.size + document.resources.size <= 9996)
        val hrefs = (document.sections.map { it.href } + document.resources.map { it.href })
        require(hrefs.distinct().size == hrefs.size)
        hrefs.forEach { require(safePath(it) && it !in setOf("package.opf", "nav.xhtml")) }
        val root = lease.directory.canonicalFile
        (document.sections.map { it.xhtmlFile } + document.resources.map { it.file }).forEach {
            require(lease.ownsArtifact(it) && it.canonicalFile.toPath().startsWith(root.toPath())) { "Input must be staged in the session" }
        }
        require(document.resources.all { it.mediaType in setOf("image/png", "image/jpeg", "image/gif", "image/webp", "text/css") }) { "Only passive raster and restricted style resources are supported" }
        require((document.sections.map { it.xhtmlFile } + document.resources.map { it.file }).sumOf { it.length() } <= 2L * 1024 * 1024 * 1024) { "Expanded publication exceeds 2 GiB" }
        var normalizedUnits = 0L
        return lease.writeArtifact { output ->
            ZipOutputStream(output).use { zip ->
                fun entry(name: String, content: String) {
                    zip.putNextEntry(ZipEntry(name).apply { time = 0 })
                    zip.write(content.toByteArray(Charsets.UTF_8)); zip.closeEntry()
                }
                val mimetype = "application/epub+zip".toByteArray()
                zip.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; time = 0; size = mimetype.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mimetype) }.value })
                zip.write(mimetype); zip.closeEntry()
                entry("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="EPUB/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
                entry("EPUB/package.opf", packageXml(document))
                entry("EPUB/nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>${escape(document.title)}</title></head><body><nav epub:type="toc"><ol>""" + document.sections.mapIndexed { index, section -> """<li><a href="${escape(section.href)}">${escape(section.title ?: "${index + 1}")}</a></li>""" }.joinToString("") + "</ol></nav></body></html>")
                document.sections.forEach { section ->
                    val text = section.xhtmlFile.reader(Charsets.UTF_8).use { reader ->
                        val buffer = CharArray(65537)
                        var count = 0
                        while (count < buffer.size) {
                            val read = reader.read(buffer, count, buffer.size - count)
                            if (read < 0) break
                            count += read
                        }
                        require(count <= 65536) { "Normalized section exceeds 64 Ki UTF-16 units" }
                        String(buffer, 0, count)
                    }
                    zip.putNextEntry(ZipEntry("EPUB/${section.href}").apply { time = 0 })
                    normalizedUnits += writeXhtml(text, section.href, hrefs.toSet(), zip)
                    require(normalizedUnits <= 32L * 1024 * 1024) { "Normalized text exceeds 64 MiB" }
                    zip.closeEntry()
                }
                document.resources.forEach { resource ->
                    zip.putNextEntry(ZipEntry("EPUB/${resource.href}").apply { time = 0 })
                    if (resource.mediaType == "text/css") {
                        require(resource.file.length() <= 65536) { "Style exceeds 64 KiB" }
                        val css = resource.file.readText(Charsets.UTF_8)
                        require(css.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".#,:;{}%+-" }) { "Active or referenced CSS is unsupported" }
                        zip.write(css.toByteArray(Charsets.UTF_8))
                    } else resource.file.inputStream().use { it.copyTo(zip, 65536) }
                    zip.closeEntry()
                }
            }
        }
    }

    private fun writeXhtml(text: String, href: String, available: Set<String>, output: java.io.OutputStream): Long {
        require(!text.contains("<!DOCTYPE", ignoreCase = true) && !text.contains("<!ENTITY", ignoreCase = true))
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }
        val dom = factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
        val pending = java.util.ArrayDeque<org.w3c.dom.Node>().apply { add(dom) }
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            require(node.nodeType != org.w3c.dom.Node.PROCESSING_INSTRUCTION_NODE) { "Processing instructions are unsupported" }
            var child = node.firstChild
            while (child != null) {
                pending.add(child)
                child = child.nextSibling
            }
        }
        require(dom.documentElement.localName == "html" && dom.documentElement.namespaceURI == "http://www.w3.org/1999/xhtml")
        val elements = dom.getElementsByTagName("*")
        var paragraph = 0
        val ids = mutableSetOf<String>()
        for (index in 0 until elements.length) {
            val element = elements.item(index) as Element
            if (element.hasAttribute("id")) require(ids.add(element.getAttribute("id"))) { "Duplicate element ID" }
        }
        for (index in 0 until elements.length) {
            val element = elements.item(index) as Element
            val name = element.localName ?: element.tagName
            require(element.namespaceURI == "http://www.w3.org/1999/xhtml" && name in setOf(
                "html", "head", "title", "body", "section", "article", "div", "span", "p", "a", "img", "br", "hr",
                "h1", "h2", "h3", "h4", "h5", "h6", "ol", "ul", "li", "dl", "dt", "dd", "blockquote", "pre", "code",
                "em", "strong", "b", "i", "u", "s", "small", "sub", "sup", "table", "thead", "tbody", "tfoot", "tr", "th", "td",
                "figure", "figcaption", "aside", "nav", "header", "footer", "link",
            )) { "Active content is unsupported" }
            if (name == "link") require(element.getAttribute("rel") == "stylesheet")
            for (attributeIndex in 0 until element.attributes.length) {
                val attribute = element.attributes.item(attributeIndex)
                require(attribute.nodeName in setOf("id", "class", "title", "lang", "xml:lang", "xmlns", "xmlns:epub", "epub:type",
                    "href", "src", "alt", "width", "height", "rel", "type", "colspan", "rowspan", "scope")) { "Unsupported XHTML attribute" }
                if (attribute.localName in setOf("src", "href", "poster", "data", "srcset") || attribute.nodeName in setOf("src", "href", "srcset")) {
                    require(attribute.nodeName != "srcset")
                    val reference = URI(attribute.nodeValue)
                    require(!reference.isAbsolute && reference.rawAuthority == null && !reference.path.startsWith("/") && !attribute.nodeValue.contains('\\'))
                    val resolved = URI(href).resolve(reference).normalize()
                    require(safePath(resolved.path) && resolved.path in available) { "Resource is outside this publication" }
                }
            }
            if (name == "p" && !element.hasAttribute("id")) {
                var id: String
                do { id = "p%06d".format(java.util.Locale.ROOT, ++paragraph) } while (!ids.add(id))
                element.setAttribute("id", id)
            }
        }
        if (!dom.documentElement.hasAttribute("id")) {
            var id = "section"
            while (!ids.add(id)) id += "_"
            dom.documentElement.setAttribute("id", id)
        }
        val transformer = TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(OutputKeys.ENCODING, "UTF-8"); setOutputProperty(OutputKeys.INDENT, "no") }
        var serializedUnits = 0L
        val encoded = output.writer(Charsets.UTF_8)
        val bounded = object : java.io.Writer() {
            override fun write(chars: CharArray, offset: Int, length: Int) {
                require(serializedUnits + length <= 65536) { "Serialized section exceeds 64 Ki UTF-16 units" }
                serializedUnits += length
                encoded.write(chars, offset, length)
            }
            override fun flush() = encoded.flush()
            override fun close() = flush()
        }
        transformer.transform(DOMSource(dom), StreamResult(bounded))
        bounded.flush()
        return serializedUnits
    }

    private fun packageXml(document: NormalizedDocument): String =
        """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="book-id">${escape(document.id)}</dc:identifier><dc:title>${escape(document.title)}</dc:title><dc:language>und</dc:language><meta property="dcterms:modified">2000-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""" +
            document.sections.mapIndexed { index, section -> """<item id="s${index + 1}" href="${escape(section.href)}" media-type="application/xhtml+xml"/>""" }.joinToString("") +
            document.resources.mapIndexed { index, resource -> """<item id="r${index + 1}" href="${escape(resource.href)}" media-type="${escape(resource.mediaType)}"/>""" }.joinToString("") +
            "</manifest><spine>" + document.sections.indices.joinToString("") { """<itemref idref="s${it + 1}"/>""" } + "</spine></package>"

    private fun safePath(path: String): Boolean = path.isNotBlank() && path.split('/').all { it.isNotBlank() && it != "." && it != ".." } && path.none { it in "\\:%?#" }
    private fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
