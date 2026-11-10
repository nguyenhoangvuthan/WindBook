package dev.kent.windbook.core.model

import org.junit.Assert.*
import org.junit.Test

class ReaderNavigationTest {
    @Test fun seekUsesPhysicalEndpointsWithoutChangingLegacyProgression() {
        assertEquals(0, seekPage(0.0, 100))
        assertEquals(99, seekPage(1.0, 100))
        assertEquals(50, seekPage(.5, 101))
        assertEquals(0, seekPage(.8, 1))
        assertNull(seekPage(Double.NaN, 100))
        assertNull(seekPage(.4, 0))
    }
    @Test fun historyRetainsFullAnchorsAndEvictsOldest() {
        val history = ReaderJumpHistory()
        repeat(12) { history.record("origin-$it", "target-$it") }
        assertEquals(10, history.size)
        assertEquals("origin-11", history.pop())
        repeat(8) { history.pop() }
        assertEquals("origin-2", history.pop())
        assertNull(history.pop())
    }
    @Test fun unchangedOrFailedJumpsDoNotEnterHistory() {
        val history = ReaderJumpHistory()
        history.record(null, "next")
        history.record("same", "same")
        assertEquals(0, history.size)
    }
    @Test fun contentsRejectsBadTargetsButKeepsGroupingChildren() {
        val result = normalizePdfContents(listOf(ContentsEntry("a", "Part", null,
            listOf(ContentsEntry("b", "Tiếng Việt", 2), ContentsEntry("c", "Bad", 999)))), 10)
        assertEquals(ContentsStatus.READY, result.status)
        assertNull(result.entries[0].page)
        assertEquals(2, result.entries[0].children[0].page)
        assertNull(result.entries[0].children[1].page)
    }
    @Test fun overLimitContentsAreUnavailableRatherThanFalselyEmpty() {
        val result = normalizePdfContents(List(10001) { ContentsEntry("$it", "Entry", 1) }, 1)
        assertEquals(ContentsStatus.UNAVAILABLE, result.status)
        assertTrue(result.entries.isEmpty())
        assertEquals(ContentsStatus.EMPTY, normalizePdfContents(emptyList(), 1).status)
    }
}
