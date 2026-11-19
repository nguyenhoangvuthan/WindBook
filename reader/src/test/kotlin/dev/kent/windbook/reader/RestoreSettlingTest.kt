package dev.kent.windbook.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSettlingTest {
    private val tall = PageLayout(width = 411, height = 874, scrollWidth = 1645)
    private val padded = PageLayout(width = 411, height = 794, scrollWidth = 1645)

    @Test fun visibleAnchorInAnUnchangedLayoutSettlesAfterTheWindow() {
        val settling = RestoreSettling(settleMillis = 1000)
        assertFalse(settling.observe(now = 0, layout = tall, anchorVisible = true))
        assertFalse(settling.observe(now = 600, layout = tall, anchorVisible = true))
        assertTrue(settling.observe(now = 1000, layout = tall, anchorVisible = true))
    }

    /** The reported bug: Readium adds its vertical padding after the first check, so the page re-flows. */
    @Test fun layoutChangeInsideTheWindowRestartsIt() {
        val settling = RestoreSettling(settleMillis = 1000)
        assertFalse(settling.observe(now = 0, layout = tall, anchorVisible = true))
        assertFalse(settling.observe(now = 500, layout = padded, anchorVisible = true))
        assertFalse(settling.observe(now = 1200, layout = padded, anchorVisible = true))
        assertTrue(settling.observe(now = 1500, layout = padded, anchorVisible = true))
    }

    @Test fun hiddenAnchorRestartsTheWindow() {
        val settling = RestoreSettling(settleMillis = 1000)
        settling.observe(now = 0, layout = tall, anchorVisible = true)
        assertFalse(settling.observe(now = 900, layout = tall, anchorVisible = false))
        assertFalse(settling.observe(now = 1100, layout = tall, anchorVisible = true))
        assertTrue(settling.observe(now = 2100, layout = tall, anchorVisible = true))
    }

    @Test fun unknownLayoutNeverSettles() {
        val settling = RestoreSettling(settleMillis = 1000)
        assertFalse(settling.observe(now = 0, layout = null, anchorVisible = true))
        assertFalse(settling.observe(now = 5000, layout = null, anchorVisible = true))
    }

    @Test fun layoutIsParsedFromTheMeasurementScriptResult() {
        assertEquals(tall, parsePageLayout("\"411,874,1645\""))
        assertEquals(tall, parsePageLayout("411,874,1645"))
        assertNull(parsePageLayout("null"))
        assertNull(parsePageLayout("411,874"))
        assertNull(parsePageLayout(null))
    }
}
