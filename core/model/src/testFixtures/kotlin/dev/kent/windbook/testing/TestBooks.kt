package dev.kent.windbook.testing

import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Generates tiny, valid book files for tests. */
object TestBooks {

    fun epub(
        dir: File,
        name: String = "book.epub",
        title: String = "Test Book",
        author: String = "Ada Writer",
        missingManifestItem: Boolean = false,
    ): File {
        // Like many calibre books: the manifest lists a stylesheet the ZIP doesn't contain, and the chapter links it.
        val missingItem = if (missingManifestItem) """<item id="css" href="Styles/missing.css" media-type="text/css"/>""" else ""
        val missingLink = if (missingManifestItem) """<link rel="stylesheet" type="text/css" href="Styles/missing.css"/>""" else ""
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            // OCF: "mimetype" first and stored uncompressed.
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(
                ZipEntry("mimetype").apply {
                    method = ZipEntry.STORED
                    size = mime.size.toLong()
                    compressedSize = mime.size.toLong()
                    crc = CRC32().apply { update(mime) }.value
                },
            )
            zip.write(mime)
            zip.closeEntry()
            zip.text(
                "META-INF/container.xml",
                """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""",
            )
            zip.text(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:${name.hashCode()}</dc:identifier><dc:title>$title</dc:title><dc:creator>$author</dc:creator><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>$missingItem</manifest><spine><itemref idref="c1"/></spine></package>""",
            )
            zip.text(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Chapter One</a></li></ol></nav></body></html>""",
            )
            zip.text(
                "OEBPS/c1.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter One</title>$missingLink</head><body><h1>Chapter One</h1><p>Hello WindBook.</p></body></html>""",
            )
        }
        return file
    }

    /** Header-valid PDF; good enough for format sniffing, not for rendering. */
    fun pdf(dir: File, name: String = "doc.pdf"): File =
        File(dir, name).apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }

    fun zip(dir: File, name: String, entries: Map<String, ByteArray>): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray())
        closeEntry()
    }
}
