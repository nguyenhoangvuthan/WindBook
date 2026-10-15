package dev.kent.windbook.reader.pdf

import dev.kent.windbook.reader.toJsonString
import dev.kent.windbook.reader.toLocatorOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfLocatorsTest {
    @Test fun m1LocatorReopensSamePage() {
        // Exactly what M1's Readium PDF navigator saved.
        val json = """{"href":"publication.pdf","type":"application/pdf","locations":{"fragments":["page=21"],"progression":0.0,"position":21,"totalProgression":0.1149}}"""
        assertEquals(20, pageIndexOf(json.toLocatorOrNull(), pageCount = 174))
    }

    @Test fun missingOrBrokenLocatorOpensFirstPage() {
        assertEquals(0, pageIndexOf(null, pageCount = 10))
        assertEquals(0, pageIndexOf("""{"href":"x","type":"application/pdf"}""".toLocatorOrNull(), pageCount = 10))
    }

    @Test fun positionBeyondTheEndIsClamped() {
        val json = """{"href":"x","type":"application/pdf","locations":{"position":999}}"""
        assertEquals(9, pageIndexOf(json.toLocatorOrNull(), pageCount = 10))
    }

    @Test fun ownLocatorsMatchTheM1Format() {
        // Same fields M1's Readium navigator saved, so old and new progress read the same way.
        val locator = pdfLocator(page = 23, pageCount = 258)
        assertEquals("publication.pdf", locator.href.toString())
        assertEquals("application/pdf", locator.mediaType.toString())
        assertEquals(24, locator.locations.position)
        assertEquals(listOf("page=24"), locator.locations.fragments)
        assertEquals(23.0 / 258, locator.locations.totalProgression!!, 1e-9)
        assertEquals(23, pageIndexOf(locator.toJsonString().toLocatorOrNull(), pageCount = 258))
    }
}
