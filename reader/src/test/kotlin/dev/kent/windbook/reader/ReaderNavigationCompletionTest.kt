package dev.kent.windbook.reader

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderNavigationCompletionTest {
    private fun locator(position: Int,selector: String?=null)=
        """{"href":"chapter.xhtml","type":"application/xhtml+xml","locations":{"position":$position${selector?.let {",\"cssSelector\":\"$it\""} ?: ""}}}""".toLocatorOrNull()!!
    @Test fun unrelatedPositionChangeDoesNotCompleteRequestedTarget() {
        assertFalse(navigationTargetMatches(locator(20),locator(2)))
        assertTrue(navigationTargetMatches(locator(20),locator(20)))
    }
    @Test fun semanticTargetNeedsVisibleElementEvenAtTheSameCoarsePosition() {
        val target=locator(20,"#passage")
        assertFalse(navigationTargetMatches(target,locator(20),false))
        assertTrue(navigationTargetMatches(target,locator(20),true))
    }
    @Test fun savedAnchorsKeepVisibleSelectorAlongsideLegacyPosition() {
        val merged=semanticReadingLocator(locator(20),locator(1,"#passage"))
        assertEquals("#passage",merged.locations.otherLocations["cssSelector"])
        assertEquals(20,merged.locations.position)
    }
}
