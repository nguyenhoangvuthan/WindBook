package dev.kent.windbook.reader

import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadiumEngineTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var services: ReadiumServices

    @Before fun setUp() {
        // PDFium needs native code that Robolectric can't load; EPUB doesn't need it.
        services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
    }

    @Test fun extractsTitleAndAuthorFromEpub() = runTest {
        val epub = TestBooks.epub(tmp.root, title = "Dế Mèn Phiêu Lưu Ký", author = "Tô Hoài")

        val meta = ReadiumMetadataExtractor(services).extract(epub, BookFormat.EPUB).getOrThrow()

        assertEquals("Dế Mèn Phiêu Lưu Ký", meta.title)
        assertEquals("Tô Hoài", meta.author)
        assertNull(meta.coverWebp)
    }

    @Test fun extractFailsForGarbage() = runTest {
        val junk = tmp.newFile("junk.epub").apply { writeText("not a zip") }
        assertTrue(ReadiumMetadataExtractor(services).extract(junk, BookFormat.EPUB).isFailure)
    }

    @Test fun engineOpensEpubSession() = runTest {
        val session = ReaderEngine(services).open(TestBooks.epub(tmp.root), BookFormat.EPUB).getOrThrow()
        assertEquals(BookFormat.EPUB, session.format)
        assertTrue((session as ReadiumReaderSession).positionCount >= 1)
        session.close()
    }

    @Test fun engineFailsForMissingFile() = runTest {
        assertTrue(ReaderEngine(services).open(tmp.root.resolve("missing.epub"), BookFormat.EPUB).isFailure)
    }

    @Test fun engineRejectsUnsupportedFormatsEvenForReadableEpubBytes() = runTest {
        val epub = TestBooks.epub(tmp.root)

        unsupportedFormats.forEach { format ->
            val result = ReaderEngine(services).open(epub, format)
            result.getOrNull()?.close()

            assertUnsupported(format, result.exceptionOrNull())
        }
    }

    @Test fun engineRejectsUnsupportedFormatsBeforeCheckingTheFile() = runTest {
        unsupportedFormats.forEach { format ->
            val result = ReaderEngine(services).open(tmp.root.resolve("missing.${format.extension}"), format)

            assertUnsupported(format, result.exceptionOrNull())
        }
    }

    @Test fun metadataRejectsUnsupportedFormatsEvenForReadableEpubBytes() = runTest {
        val epub = TestBooks.epub(tmp.root)

        unsupportedFormats.forEach { format ->
            val result = ReadiumMetadataExtractor(services).extract(epub, format)

            assertUnsupported(format, result.exceptionOrNull())
        }
    }

    @Test fun metadataRejectsUnsupportedFormatsBeforeOpeningTheFile() = runTest {
        unsupportedFormats.forEach { format ->
            val result = ReadiumMetadataExtractor(services).extract(tmp.root.resolve("missing.${format.extension}"), format)

            assertUnsupported(format, result.exceptionOrNull())
        }
    }

    private fun assertUnsupported(format: BookFormat, error: Throwable?) {
        assertTrue("$format must report unsupported format, got $error", error is ReaderException)
        assertTrue("$format must report unsupported format, got $error", error?.message.orEmpty().contains("Unsupported format"))
    }

    private val unsupportedFormats = listOf(BookFormat.MOBI, BookFormat.AZW3, BookFormat.FB2, BookFormat.CBZ, BookFormat.CBR, BookFormat.TXT)

    @Test fun locatorCodecReadsTotalProgression() {
        val json = """{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"totalProgression":0.37}}"""
        assertEquals(0.37, LocatorCodec.progression(json)!!, 1e-9)
        assertNull(LocatorCodec.progression("{broken"))
    }
}
