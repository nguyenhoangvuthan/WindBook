package dev.kent.windbook.reader.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import dev.kent.windbook.testing.TestBooks
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpubMetadataFastPathTest {
    @get:Rule val tmp = TemporaryFolder()
    private val reader = EpubMetadataFastPath()

    private fun raster(width: Int = 1200, height: Int = 1800): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        return try { ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray() } }
        finally { bitmap.recycle() }
    }

    private fun epub(
        epub2: Boolean = false,
        href: String = "Images/cover.png",
        image: String = "OPS/Images/cover.png",
        media: String = "image/png",
        extraMeta: String = "",
        extraManifest: String = "",
        cover: ByteArray? = raster(),
        container: String = "OPS/content.opf",
        title: String = "  Sách &amp; tiếng Việt  ",
    ): File {
        val entries = linkedMapOf(
            "mimetype" to "application/epub+zip".toByteArray(),
            "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="$container" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
            "OPS/content.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="${if (epub2) "2.0" else "3.0"}"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>$title</dc:title><dc:creator>Tác giả</dc:creator>${if (epub2) "<meta name=\"cover\" content=\"cover\"/>" else ""}$extraMeta</metadata><manifest><item id="cover" href="$href" media-type="$media" ${if (!epub2) "properties=\"cover-image\"" else ""}/>$extraManifest<item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray(),
        )
        entries["OPS/chapter.xhtml"] = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter</title></head><body><p>Test</p></body></html>".toByteArray()
        if (cover != null) entries[image] = cover
        return TestBooks.zip(tmp.root, "book.epub", entries)
    }

    @Test fun epub3ReadsUnicodeMetadataAndBoundsTheActualCover() = runBlocking {
        val result = reader.read(epub())
        assertTrue("Ordinary EPUB3 should avoid opening a full publication: $result", result is FastEpubMetadata.Success)
        val metadata = (result as FastEpubMetadata.Success).metadata
        assertEquals("Sách & tiếng Việt", metadata.title)
        assertEquals("Tác giả", metadata.author)
        val bytes = metadata.coverWebp!!
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        assertEquals(400, options.outWidth)
        assertEquals(600, options.outHeight)
    }

    @Test fun epub2ResolvesItsCoverIdAndEscapedRelativePath() = runBlocking {
        assertTrue(reader.read(epub(epub2 = true, href = "Images/front%20cover.png", image = "OPS/Images/front cover.png")) is FastEpubMetadata.Success)
    }

    @Test fun relativeParentInsideTheArchiveIsAllowed() = runBlocking {
        assertTrue(reader.read(epub(href = "../Images/front.png", image = "Images/front.png")) is FastEpubMetadata.Success)
    }

    @Test fun multipleTitlesAndRefinementsFallBackInsteadOfGuessing() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(extraMeta = "<dc:title>Alternate</dc:title>")))
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(extraMeta = "<meta refines=\"#title\" property=\"title-type\">main</meta>")))
    }

    @Test fun globalDublinCoreOverridesFallBackToReadiumsMetadataRules() = runBlocking {
        for (property in listOf("dcterms:title", "dcterms:creator", "dcterms:contributor")) {
            assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(extraMeta = "<meta property=\"$property\">Alternate</meta>")))
        }
    }

    @Test fun dublinCoreContributorsAreNotSilentlyDropped() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(extraMeta =
            "<dc:contributor xmlns:opf=\"http://www.idpf.org/2007/opf\" opf:role=\"aut\">Primary</dc:contributor>")))
    }

    @Test fun missingCanonicalSpineCannotBypassPublicationValidation() = runBlocking {
        val original = epub()
        val entries = java.util.zip.ZipFile(original).use { zip -> zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).use { it.readBytes() }.let { bytes ->
                if (entry.name.endsWith(".opf")) bytes.toString(Charsets.UTF_8).replace("<spine><itemref idref=\"chapter\"/></spine>", "").toByteArray() else bytes
            }
        } }
        assertEquals(FastEpubMetadata.Unsupported, reader.read(TestBooks.zip(tmp.root, "no-spine.epub", entries)))
    }

    @Test fun guideElementsCannotMasqueradeAsCanonicalMetadataOrManifest() = runBlocking {
        val original = epub()
        val entries = java.util.zip.ZipFile(original).use { zip -> zip.entries().asSequence().associate { entry ->
            entry.name to zip.getInputStream(entry).use { it.readBytes() }
        } }
        val opf = entries.getValue("OPS/content.opf").toString(Charsets.UTF_8)
        val coverItem = Regex("<item id=\"cover\"[^>]*/>").find(opf)!!.value
        val misplacedCover = opf.replace(coverItem, "").replace("</package>", "<guide>$coverItem</guide></package>")
        assertEquals(FastEpubMetadata.Unsupported, reader.read(TestBooks.zip(tmp.root, "misplaced-cover.epub", entries + ("OPS/content.opf" to misplacedCover.toByteArray()))))
        val title = Regex("<dc:title>.*?</dc:title>").find(opf)!!.value
        val misplacedTitle = opf.replace(title, "").replace("</package>",
            "<guide xmlns:dc=\"http://purl.org/dc/elements/1.1/\">$title</guide></package>")
        assertEquals(FastEpubMetadata.Unsupported, reader.read(TestBooks.zip(tmp.root, "misplaced-title.epub", entries + ("OPS/content.opf" to misplacedTitle.toByteArray()))))
    }

    @Test fun unsupportedSvgMissingCoverAndMultipleCoverDeclarationsUseFallback() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(media = "image/svg+xml", cover = "<svg/>".toByteArray())))
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(cover = null)))
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(extraManifest = "<item id=\"second\" href=\"second.png\" media-type=\"image/png\" properties=\"cover-image\"/>")))
    }

    @Test fun noDeclaredCoverKeepsTheLegacyCoverFallbackAvailable() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(TestBooks.epub(tmp.root)))
    }

    @Test fun textOnlyArchivesSkipTheExtraXmlParseBeforeLegacyFallback() = runBlocking {
        val stages = mutableListOf<String>()
        val reader = EpubMetadataFastPath { stage, _ -> stages.add(stage) }
        assertEquals(FastEpubMetadata.Unsupported, reader.read(TestBooks.epub(tmp.root)))
        assertFalse("Text-only archives cannot use the raster fast path", "xml" in stages)
    }

    @Test fun unsafeArchiveAndEscapesCannotFallBackThroughTheGuard() = runBlocking {
        assertTrue(reader.read(epub(href = "../../outside.png")) is FastEpubMetadata.Invalid)
        assertTrue(reader.read(epub(container = "../outside.opf")) is FastEpubMetadata.Invalid)
        val invalid = TestBooks.zip(tmp.root, "unsafe.epub", mapOf("META-INF/container.xml" to byteArrayOf(), "../outside" to byteArrayOf()))
        assertTrue(reader.read(invalid) is FastEpubMetadata.Invalid)
    }

    @Test fun externalImageUrisAreNotFetched() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(href = "https://example.invalid/cover.png")))
    }

    @Test fun malformedOrOversizedXmlAndInvalidRasterAreFallbackCases() = runBlocking {
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(title = "<broken>")))
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(title = "a".repeat(1_048_577))))
        assertEquals(FastEpubMetadata.Unsupported, reader.read(epub(cover = byteArrayOf(1, 2, 3))))
    }

    @Test fun cancellationIsNotConvertedToUnsupported() = runBlocking {
        val file = epub()
        var cancelled = false
        val job = launch {
            currentCoroutineContext().cancel()
            try { reader.read(file) } catch (_: CancellationException) { cancelled = true }
        }
        job.join()
        assertTrue(cancelled)
    }
}
