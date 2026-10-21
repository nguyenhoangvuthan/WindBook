package dev.kent.windbook.reader.pdf

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.MarginDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDocumentHandleTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    @Test fun readsPageCountAndSizes() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir, pages = 3)).use { doc ->
            assertEquals(3, doc.pageCount)
            assertEquals(400f, doc.pageSize(0).width, 0.5f)
            assertEquals(600f, doc.measure(2).height, 0.5f)
        }
    }

    @Test fun luminanceFindsTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val l = doc.luminance(0)
            val box = MarginDetector.contentBox(l.values, l.width, l.height)!!
            // Box 100..300 × 150..450 on 400×600 → 0.25..0.75, padded by 0.015.
            assertEquals(0.235f, box.left, 0.02f)
            assertEquals(0.235f, box.top, 0.02f)
            assertEquals(0.765f, box.right, 0.02f)
            assertEquals(0.765f, box.bottom, 0.02f)
        }
    }

    @Test fun croppedRenderFillsTheBitmapWithTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val crop = CropBox(0.25f, 0.25f, 0.75f, 0.75f)
            val bmp = doc.render(0, crop, widthPx = 500)
            assertEquals(500, bmp.width)
            assertEquals(renderedHeight(doc.pageSize(0), crop, 500), bmp.height)
            // Inside the crop everything is the black box, corners included.
            assertEquals(Color.BLACK, bmp.getPixel(5, 5) or 0xFF000000.toInt())
            assertEquals(Color.BLACK, bmp.getPixel(bmp.width - 5, bmp.height - 5) or 0xFF000000.toInt())
        }
    }

    @Test fun fullPageRenderIsWhiteAroundTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val bmp = doc.render(0, CropBox.FULL, widthPx = 400)
            assertEquals(Color.WHITE, bmp.getPixel(10, 10))
            assertEquals(Color.BLACK, bmp.getPixel(200, 300))
        }
    }

    @Test fun regionRenderIsATileOfTheZoomedPage() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            // Page at 800 px wide (2×): the box starts at x = 200, y = 300. Tile 100×100 at (150, 250).
            val tile = doc.render(0, CropBox.FULL, widthPx = 800, region = PixelRegion(150, 250, 100, 100))
            assertEquals(100, tile.width)
            assertEquals(Color.WHITE, tile.getPixel(10, 10)) // (160, 260): outside the box
            assertEquals(Color.BLACK, tile.getPixel(90, 90)) // (240, 340): inside the box
        }
    }

    @Test fun garbageIsRejected() = runBlocking {
        val junk = dir.resolve("junk.pdf").apply { writeText("%PDF-1.4 not really") }
        assertTrue(runCatching { PdfDocumentHandle.open(junk) }.isFailure)
    }

    /** Review #3: converting a 1200 px render to luminance (~2.3 M pixels) must not run on the caller (the UI thread). */
    @Test fun luminanceDoesItsWorkOffTheCallingThread() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.scanned(dir, "luma-thread.pdf", pages = 1)).use { doc ->
            doc.luminance(0, 1200) // warm up
            val before = android.os.SystemClock.currentThreadTimeMillis()
            repeat(3) { doc.luminance(0, 1200) }
            val callerCpu = android.os.SystemClock.currentThreadTimeMillis() - before
            assertTrue("the calling thread spent $callerCpu ms of CPU", callerCpu < 15)
        }
    }
}
