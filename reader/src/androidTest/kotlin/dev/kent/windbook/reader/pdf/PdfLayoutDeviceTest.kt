package dev.kent.windbook.reader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.CropBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfLayoutDeviceTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private val vietnamese = listOf(
        "Người đàn ông lịch lãm biết cách",
        "Thể hiện ước mơ đẹp nhất của mình",
        "Kiếp nghèo, kiếp cầm ca, kiếp đỏ đen",
        "Ở các nước Âu, Mỹ, phương pháp này",
    )

    @Test fun vietnameseTextIsReadAsLinesAndWords() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.textLines(dir, "vi-text.pdf", vietnamese)).use { doc ->
            val layout = PdfPages(doc).pageLayout(0, CropBox.FULL)!!
            assertEquals("lines: ${layout.textLines.map { it.words.size }}", vietnamese.size, layout.textLines.size)
            assertEquals(7, layout.textLines[0].words.size)
        }
    }

    @Test fun layoutsAreCachedPerPageAndCrop() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.textLines(dir, "vi-cache.pdf", vietnamese)).use { doc ->
            val pages = PdfPages(doc)
            assertSame(pages.pageLayout(0, CropBox.FULL), pages.pageLayout(0, CropBox.FULL))
        }
    }

    /** Review #3: converting a 1200 px render to luminance (~2.3 M pixels) must not run on the caller (the UI thread). */
    @Test fun luminanceDoesItsWorkOffTheCallingThread() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.scanned(dir, "luma-thread.pdf", pages = 1)).use { doc ->
            doc.luminance(0, ANALYSIS_WIDTH) // warm up
            val before = android.os.SystemClock.currentThreadTimeMillis()
            repeat(3) { doc.luminance(0, ANALYSIS_WIDTH) }
            val callerCpu = android.os.SystemClock.currentThreadTimeMillis() - before
            assertTrue("the calling thread spent $callerCpu ms of CPU", callerCpu < 15)
        }
    }
}
