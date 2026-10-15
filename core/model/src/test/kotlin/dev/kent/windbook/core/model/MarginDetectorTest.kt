package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginDetectorTest {
    private val w = 300
    private val h = 400

    /** White page (255) with black (0) filled where [ink] is true. */
    private fun page(ink: (x: Int, y: Int) -> Boolean) = ByteArray(w * h) { i ->
        if (ink(i % w, i / w)) 0 else 255.toByte()
    }

    private fun assertBox(expected: CropBox, actual: CropBox?, tol: Float = 0.01f) {
        actual!!
        assertEquals(expected.left, actual.left, tol)
        assertEquals(expected.top, actual.top, tol)
        assertEquals(expected.right, actual.right, tol)
        assertEquals(expected.bottom, actual.bottom, tol)
    }

    @Test fun textBlockGetsPaddedBox() {
        val luma = page { x, y -> x in 60 until 240 && y in 80 until 320 }
        // 60/300 = 0.20, 80/400 = 0.20, 240/300 = 0.80, 320/400 = 0.80, each padded by 0.015.
        assertBox(CropBox(0.185f, 0.185f, 0.815f, 0.815f), MarginDetector.contentBox(luma, w, h))
    }

    @Test fun isolatedScanSpecksAreIgnored() {
        val luma = page { x, y -> (x in 60 until 240 && y in 80 until 320) || (x == 5 && y == 5) || (x == 290 && y == 390) }
        assertBox(CropBox(0.185f, 0.185f, 0.815f, 0.815f), MarginDetector.contentBox(luma, w, h))
    }

    @Test fun blankPageHasNoBox() = assertNull(MarginDetector.contentBox(page { _, _ -> false }, w, h))

    @Test fun fullBleedImageIsNotCropped() {
        val luma = page { _, _ -> true }
        assertBox(CropBox.FULL, MarginDetector.forPage(MarginDetector.bookBox(listOf(MarginDetector.contentBox(luma, w, h))), null))
    }

    @Test fun cropNeverExceedsAQuarterPerSide() {
        val tiny = CropBox(0.45f, 0.45f, 0.55f, 0.55f)
        val box = tiny.clamped()
        assertEquals(0.25f, box.left, 1e-6f)
        assertEquals(0.75f, box.right, 1e-6f)
    }

    @Test fun bookBoxUsesWideEdgesAndSkipsNearBlankSamples() {
        val samples = listOf(
            CropBox(0.10f, 0.10f, 0.90f, 0.90f),
            CropBox(0.12f, 0.11f, 0.88f, 0.89f),
            CropBox(0.11f, 0.12f, 0.89f, 0.88f),
            CropBox(0.48f, 0.48f, 0.52f, 0.52f), // near blank: ignored
            null,
        )
        val box = MarginDetector.bookBox(samples)
        assertTrue(box.left <= 0.11f && box.right >= 0.89f)
        assertTrue(box.top <= 0.11f && box.bottom >= 0.89f)
    }

    @Test fun noUsableSamplesMeansNoCrop() = assertEquals(CropBox.FULL, MarginDetector.bookBox(listOf(null)))

    @Test fun aWideFigurePageGrowsBeyondTheBookBox() {
        val book = CropBox(0.15f, 0.12f, 0.85f, 0.88f)
        val figure = CropBox(0.05f, 0.30f, 0.95f, 0.60f)
        assertEquals(CropBox(0.05f, 0.12f, 0.95f, 0.88f), MarginDetector.forPage(book, figure))
    }

    @Test fun mirroredMarginsGetOneBoxPerParity() {
        val plan = CropPlan(odd = CropBox(0.20f, 0.1f, 0.90f, 0.9f), even = CropBox(0.10f, 0.1f, 0.80f, 0.9f))
        assertEquals(0.20f, plan.boxFor(0).left) // page 1
        assertEquals(0.10f, plan.boxFor(1).left) // page 2
    }

    @Test fun samplingSkipsFrontMatterAndSpreadsPerParity() {
        val indices = MarginDetector.sampleIndices(pageCount = 300)
        assertTrue(indices.all { it in 4 until 298 })
        assertEquals(12, indices.count { it % 2 == 0 })
        assertEquals(12, indices.count { it % 2 == 1 })
        assertEquals(indices.sorted(), indices)
    }

    @Test fun shortDocumentsSampleEveryPage() = assertEquals(listOf(0, 1, 2), MarginDetector.sampleIndices(3))
}
