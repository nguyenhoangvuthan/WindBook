package dev.kent.windbook.reader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.LocatorCodec
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.ReaderEngine
import dev.kent.windbook.reader.ReadiumServices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfEngineDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = ReaderEngine(ReadiumServices(context))

    @Test fun pdfOpensAsPdfSessionWithStableLocators() = runBlocking {
        val session = engine.open(PdfFixtures.boxed(context.cacheDir, "engine.pdf", pages = 5), BookFormat.PDF).getOrThrow()
        assertTrue(session is PdfSession)
        session as PdfSession
        assertEquals(5, session.pageCount)
        val json = session.locatorJsonFor(3)
        assertEquals(3, session.pageOf(json))
        assertEquals(0.6, LocatorCodec.progression(json)!!, 0.01) // Readium totalProgression = (4 - 1) / 5
        session.close()
    }

    @Test fun cropPlanFindsMirroredMargins() = runBlocking {
        // Both margins stay under the 25 % crop clamp (spec §3.2), so the parity difference is visible.
        val file = PdfFixtures.boxed(context.cacheDir, "mirror.pdf", pages = 12, boxL = 60f, boxR = 260f, mirrorShift = 40f)
        val session = engine.open(file, BookFormat.PDF).getOrThrow() as PdfSession
        val plan = session.pages.cropPlan()
        // Odd pages: box 100..300 of 400; even pages: 60..260.
        assertEquals(0.235f, plan.boxFor(0).left, 0.02f)
        assertEquals(0.135f, plan.boxFor(1).left, 0.02f)
        session.close()
    }

    @Test fun corruptPdfFailsToOpen() = runBlocking {
        val junk = context.cacheDir.resolve("corrupt.pdf").apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }
        assertTrue(engine.open(junk, BookFormat.PDF).isFailure)
    }
}
