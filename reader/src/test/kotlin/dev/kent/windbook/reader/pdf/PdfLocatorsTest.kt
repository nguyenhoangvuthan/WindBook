package dev.kent.windbook.reader.pdf

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
}
