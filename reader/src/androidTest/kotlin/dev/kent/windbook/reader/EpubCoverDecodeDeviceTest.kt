package dev.kent.windbook.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.metadata.EpubMetadataFastPath
import dev.kent.windbook.reader.metadata.FastEpubMetadata
import dev.kent.windbook.testing.TestBooks
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.services.cover

@RunWith(AndroidJUnit4::class)
class EpubCoverDecodeDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun epub(image: ByteArray, media: String = "image/png") = TestBooks.zip(context.cacheDir, "decode-cover.epub", mapOf(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OPS/package.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
        "OPS/package.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:decode-cover</dc:identifier><dc:title>Tiếng Việt</dc:title><dc:creator>Tác giả</dc:creator><dc:language>vi</dc:language></metadata><manifest><item id="cover" href="cover" media-type="$media" properties="cover-image"/><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray(),
        "OPS/chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><p>Test</p></body></html>""".toByteArray(),
        "OPS/cover" to image,
    ))

    private fun raster(width: Int, height: Int, jpeg: Boolean = false): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        for ((color, rect) in listOf(
            Color.RED to android.graphics.Rect(0, 0, width / 2, height / 2),
            Color.GREEN to android.graphics.Rect(width / 2, 0, width, height / 2),
            Color.BLUE to android.graphics.Rect(0, height / 2, width / 2, height),
            Color.YELLOW to android.graphics.Rect(width / 2, height / 2, width, height),
        )) { paint.color = color; canvas.drawRect(rect, paint) }
        return try {
            ByteArrayOutputStream().use { out ->
                bitmap.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

    @Test fun largeRasterHasBoundedOutputAndKeepsAllFourCorners() = runBlocking {
        val result = EpubMetadataFastPath().read(epub(raster(4000, 6000)))
        assertTrue(result is FastEpubMetadata.Success)
        val bytes = (result as FastEpubMetadata.Success).metadata.coverWebp!!
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(400, bitmap.width); assertEquals(600, bitmap.height)
            assertColor(Color.RED, bitmap.getPixel(20, 20))
            assertColor(Color.GREEN, bitmap.getPixel(380, 20))
            assertColor(Color.BLUE, bitmap.getPixel(20, 580))
            assertColor(Color.YELLOW, bitmap.getPixel(380, 580))
        } finally { bitmap.recycle() }
    }

    @Test fun jpegExifRotationMatchesItsDisplayedOrientation() = runBlocking {
        val jpeg = raster(1200, 800, jpeg = true)
        // Little-endian TIFF with one IFD0 orientation tag (6 =90 degrees clockwise).
        val exif = byteArrayOf(0x45,0x78,0x69,0x66,0,0,0x49,0x49,0x2a,0,8,0,0,0,1,0,0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0)
        val size = exif.size + 2
        val rotated = jpeg.copyOfRange(0, 2) + byteArrayOf(0xff.toByte(), 0xe1.toByte(), (size shr 8).toByte(), size.toByte()) + exif + jpeg.copyOfRange(2, jpeg.size)
        val result = EpubMetadataFastPath().read(epub(rotated, "image/jpeg")) as FastEpubMetadata.Success
        val bytes = result.metadata.coverWebp!!
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        try {
            assertEquals(400, bitmap.width); assertEquals(600, bitmap.height)
            assertColor(Color.BLUE, bitmap.getPixel(20, 20))
            assertColor(Color.RED, bitmap.getPixel(380, 20))
            assertColor(Color.YELLOW, bitmap.getPixel(20, 580))
            assertColor(Color.GREEN, bitmap.getPixel(380, 580))
        } finally { bitmap.recycle() }
    }

    @Test fun metadataMatchesReadiumAndExtractionKeepsSourceBytesUnchanged() = runBlocking {
        val file = epub(raster(400, 600))
        val original = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val services = ReadiumServices(context)
        val reference = services.openPublication(file).getOrThrow()
        val actual = try {
            val actual = ReadiumMetadataExtractor(services).extract(file, BookFormat.EPUB).getOrThrow()
            assertEquals(reference.metadata.title, actual.title)
            assertEquals(reference.metadata.authors.firstOrNull()?.name, actual.author)
            actual
        } finally { reference.close() }
        assertNotNull(actual.coverWebp)
        assertArrayEquals(original, java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
    }

    @Test fun recordsParserDecodeAndCompressionStagesWithoutProductionLogging() = runBlocking {
        val file = epub(raster(1200, 1800))
        val stages = linkedMapOf<String, Long>()
        val fast = EpubMetadataFastPath { stage, nanos -> stages[stage] = nanos }.read(file)
        assertTrue(fast is FastEpubMetadata.Success)
        assertEquals(setOf("guard", "xml", "coverRead", "decode", "compress"), stages.keys)
        val before = System.nanoTime()
        val publication = ReadiumServices(context).openPublication(file).getOrThrow()
        val opened = System.nanoTime()
        try {
            val bitmap = requireNotNull(publication.cover())
            val decoded = System.nanoTime()
            try { dev.kent.windbook.reader.metadata.CoverEncoder.encode(bitmap) } finally { bitmap.recycle() }
            val encoded = System.nanoTime()
            android.util.Log.i("WBCoverStages", "fastNanos=$stages legacyOpenNs=${opened-before} legacyCoverNs=${decoded-opened} legacyCompressNs=${encoded-decoded}")
        } finally { publication.close() }
        Unit
    }

    @Test fun extendedGlobalMetadataMatchesReadiumThroughFallback() = runBlocking {
        val original = epub(raster(400, 600))
        val file = rewritePackage(original, "global-title.epub") {
            it.replace("</metadata>", "<meta property=\"dcterms:title\">Alternate title</meta><meta property=\"dcterms:creator\">Alternate author</meta></metadata>")
        }
        assertEquals(FastEpubMetadata.Unsupported, EpubMetadataFastPath().read(file))
        val services = ReadiumServices(context)
        val publication = services.openPublication(file).getOrThrow()
        try {
            val actual = ReadiumMetadataExtractor(services).extract(file, BookFormat.EPUB).getOrThrow()
            assertEquals(publication.metadata.title, actual.title)
            assertEquals(publication.metadata.authors.firstOrNull()?.name, actual.author)
        } finally { publication.close() }
    }

    @Test fun missingSpineIsRejectedByTheFullExtractor() = runBlocking {
        val original = epub(raster(400, 600))
        val file = rewritePackage(original, "missing-spine.epub") {
            it.replace(Regex("<spine>.*?</spine>"), "")
        }
        assertEquals(FastEpubMetadata.Unsupported, EpubMetadataFastPath().read(file))
        assertTrue(ReadiumMetadataExtractor(ReadiumServices(context)).extract(file, BookFormat.EPUB).isFailure)
    }

    @Test fun contributorWithAuthorRoleRetainsReadiumAuthorMetadata() = runBlocking {
        val original = epub(raster(400, 600))
        val file = rewritePackage(original, "contributor-author.epub") {
            it.replace("<dc:creator>Tác giả</dc:creator>",
                "<dc:contributor xmlns:opf=\"http://www.idpf.org/2007/opf\" opf:role=\"aut\">Primary</dc:contributor>")
        }
        assertEquals(FastEpubMetadata.Unsupported, EpubMetadataFastPath().read(file))
        val services = ReadiumServices(context)
        val publication = services.openPublication(file).getOrThrow()
        try {
            val actual = ReadiumMetadataExtractor(services).extract(file, BookFormat.EPUB).getOrThrow()
            assertEquals("Primary", publication.metadata.authors.firstOrNull()?.name)
            assertEquals(publication.metadata.authors.firstOrNull()?.name, actual.author)
        } finally { publication.close() }
    }

    private fun rewritePackage(file: java.io.File, name: String, transform: (String) -> String): java.io.File {
        val entries = java.util.zip.ZipFile(file).use { zip -> zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).use { it.readBytes() }.let { bytes ->
                if (entry.name.endsWith(".opf")) transform(bytes.toString(Charsets.UTF_8)).toByteArray() else bytes
            }
        } }
        return TestBooks.zip(context.cacheDir, name, entries)
    }

    private fun assertColor(expected: Int, actual: Int) {
        assertTrue("Color mismatch", kotlin.math.abs(Color.red(expected) - Color.red(actual)) < 35 &&
            kotlin.math.abs(Color.green(expected) - Color.green(actual)) < 35 &&
            kotlin.math.abs(Color.blue(expected) - Color.blue(actual)) < 35)
    }
}
