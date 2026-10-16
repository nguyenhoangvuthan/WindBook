package dev.kent.windbook.reader

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.pdf.PdfFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfImportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Readium rendered a full-size cover on import: ~36 s and ~1.5 GB for the owner's 2654×4329 pt scan. */
    @Test fun largeScannedPagesImportQuicklyWithASmallCover() = runBlocking {
        val file = PdfFixtures.scanned(context.cacheDir, "import-scan.pdf")
        val started = SystemClock.elapsedRealtime()
        val meta = ReadiumMetadataExtractor(ReadiumServices(context)).extract(file, BookFormat.PDF).getOrThrow()
        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue("import took $elapsed ms", elapsed < 1500)
        val bytes = meta.coverWebp!!
        val cover = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("cover ${cover.width}×${cover.height}", cover.height in 1..600)
    }

    @Test fun aPdfWhoseCoverCannotRenderGivesNoCover() = runBlocking {
        val junk = context.cacheDir.resolve("no-cover.pdf").apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }
        assertNull(pdfCoverWebp(junk))
    }
}
