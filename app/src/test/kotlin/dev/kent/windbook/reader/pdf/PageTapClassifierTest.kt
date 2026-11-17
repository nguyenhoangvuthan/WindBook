package dev.kent.windbook.reader.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTapClassifierTest {
    private val width = 1000f
    private val classifier = PageTapClassifier(slopPx = 48f)
    private fun tap(x: Float, y: Float = 500f) = classifier.onTap(x, y, width)

    @Test fun leftEdgeTurnsBackImmediately() = assertEquals(TapAction.PREVIOUS_PAGE, tap(100f))

    @Test fun rightEdgeTurnsForwardImmediately() = assertEquals(TapAction.NEXT_PAGE, tap(900f))

    @Test fun twoQuickRightEdgeTapsTurnTwoPagesAndNeverZoom() {
        assertEquals(TapAction.NEXT_PAGE, tap(900f))
        assertEquals(TapAction.NEXT_PAGE, tap(905f))
        assertNull(classifier.flushPending())
    }

    @Test fun middleTapWaitsThenTogglesTheMenuOnce() {
        assertNull(tap(500f))
        assertTrue(classifier.isWaiting)
        assertEquals(TapAction.TOGGLE_MENU, classifier.flushPending())
        assertFalse(classifier.isWaiting)
        assertNull(classifier.flushPending())
    }

    @Test fun twoMiddleTapsBeforeTheTimeoutZoom() {
        assertNull(tap(500f))
        assertEquals(TapAction.ZOOM_TOGGLE, tap(510f))
        assertFalse(classifier.isWaiting)
        assertNull(classifier.flushPending())
    }

    @Test fun aMiddleTapAfterTheTimeoutIsAFreshFirstTap() {
        assertNull(tap(500f))
        assertEquals(TapAction.TOGGLE_MENU, classifier.flushPending())
        assertNull(tap(500f))
        assertEquals(TapAction.TOGGLE_MENU, classifier.flushPending())
    }

    @Test fun twoMiddleTapsFarApartAreNotADoubleTap() {
        assertNull(tap(400f))
        assertNull(tap(600f))
        // The first was superseded; only one lone tap remains to flush.
        assertEquals(TapAction.TOGGLE_MENU, classifier.flushPending())
        assertNull(classifier.flushPending())
    }

    @Test fun anEdgeTapWhileAMiddleTapWaitsDropsTheWait() {
        assertNull(tap(500f))
        assertEquals(TapAction.NEXT_PAGE, tap(900f))
        assertFalse(classifier.isWaiting)
        assertNull(classifier.flushPending())
    }

    @Test fun zoneBoundariesAreQuarterAndThreeQuarters() {
        assertEquals(TapAction.PREVIOUS_PAGE, tap(249f))
        assertNull(tap(250f)); classifier.flushPending()
        assertNull(tap(750f)); classifier.flushPending()
        assertEquals(TapAction.NEXT_PAGE, tap(751f))
    }
}
