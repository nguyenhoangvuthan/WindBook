package dev.kent.windbook.reader

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderControlsUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun contentsAndOverflowActionsAreReachable() {
        var opened=0
        compose.setContent {ReaderOverlay(true,"Book",null,.5,false,{}, {},onContents={opened++})}
        compose.onNodeWithContentDescription("Contents").performClick()
        assertEquals(1,opened)
        compose.onNodeWithContentDescription("More reading options").performClick()
        compose.onNodeWithText("Bookmark this location").assertIsDisplayed()
        compose.onNodeWithText("Search in book").assertIsDisplayed()
        compose.onNodeWithText("Reading appearance (Aa)").assertIsDisplayed()
    }
    @Test fun twoAppearanceFieldsSurviveBeforeObservedValueCatchesUp() {
        var latest=dev.kent.windbook.core.model.ReaderTypography()
        compose.setContent {ReaderTypographySheet(dev.kent.windbook.core.model.ReaderTypography(),dev.kent.windbook.core.model.ReaderTypography(),{},
            onChange={edit,_->latest=edit(latest)})}
        compose.onNodeWithText("Literata").performScrollTo().performClick()
        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(dev.kent.windbook.core.model.ReaderFont.LITERATA,latest.font)
        assertEquals(dev.kent.windbook.core.model.ReaderColors.DARK,latest.colors)
    }
    @Test fun seekingCommitsOnceOnRelease() {
        val jumps=mutableListOf<Double>()
        compose.setContent {ReaderPositionSlider(.2, true) {jumps+=it}}
        compose.onNodeWithContentDescription("Reading position").performTouchInput {
            down(centerLeft + androidx.compose.ui.geometry.Offset(10f,0f))
            moveTo(centerRight - androidx.compose.ui.geometry.Offset(10f,0f))
        }
        assertTrue(jumps.isEmpty())
        compose.onNodeWithContentDescription("Reading position").performTouchInput {up()}
        assertEquals(1,jumps.size)
        assertTrue(jumps.single()>.8)
    }
    @Test fun cancelledDragDoesNotDisableNextTapOrAccessibilitySeek() {
        val jumps=mutableListOf<Double>()
        compose.setContent {ReaderPositionSlider(.2,true) {jumps+=it}}
        compose.onNodeWithContentDescription("Reading position").performTouchInput {down(center);moveTo(centerRight);cancel()}
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Reading position").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(.7f)}
        assertEquals(1,jumps.size)
        compose.onNodeWithContentDescription("Reading position").performTouchInput {click(centerLeft + androidx.compose.ui.geometry.Offset(30f,0f))}
        assertEquals(2,jumps.size)
    }
    @Test fun idleSliderFollowsExternalPageChangesWithoutSeeking() {
        val fraction=androidx.compose.runtime.mutableDoubleStateOf(.1)
        val jumps=mutableListOf<Double>()
        compose.setContent {ReaderPositionSlider(fraction.doubleValue,true) {jumps+=it}}
        compose.runOnIdle {fraction.doubleValue=.9}
        compose.onNodeWithContentDescription("Reading position").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.9f,0f..1f)))
        assertTrue(jumps.isEmpty())
    }
    @Test fun activeDragIgnoresExternalPositionAndCancelShowsNewestPage() {
        val fraction=androidx.compose.runtime.mutableDoubleStateOf(.1)
        val jumps=mutableListOf<Double>()
        compose.setContent {ReaderPositionSlider(fraction.doubleValue,true) {jumps+=it}}
        compose.onNodeWithContentDescription("Reading position").performTouchInput {down(center);moveTo(centerRight)}
        compose.runOnIdle {fraction.doubleValue=.6}
        compose.onNodeWithContentDescription("Reading position").performTouchInput {cancel()}
        compose.onNodeWithContentDescription("Reading position").assert(
            SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo,
                androidx.compose.ui.semantics.ProgressBarRangeInfo(.6f,0f..1f)))
        assertTrue(jumps.isEmpty())
    }
    @Test fun cancelledSeekDoesNotNavigate() {
        val jumps=mutableListOf<Double>()
        compose.setContent {ReaderPositionSlider(.2,true) {jumps+=it}}
        compose.onNodeWithContentDescription("Reading position").performTouchInput {
            down(center);moveTo(centerRight);cancel()
        }
        assertTrue(jumps.isEmpty())
    }
}
