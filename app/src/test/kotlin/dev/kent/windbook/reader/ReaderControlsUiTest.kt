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
        // Aa moved to the top bar; it must not be duplicated in the menu.
        compose.onNodeWithText("Reading appearance (Aa)").assertDoesNotExist()
    }
    @Test fun appearanceIsOneTapAwayForReflowableBooks() {
        var taps=0
        compose.setContent {ReaderOverlay(true,"Book",null,.5,false,{}, {},onAppearance={taps++})}
        compose.onNodeWithContentDescription("Reading appearance (Aa)").performClick()
        assertEquals(1,taps)
    }
    @Test fun pdfHasNoAppearanceButton() {
        compose.setContent {ReaderOverlay(true,"Book",null,.5,false,{}, {},
            pdfDisplay=dev.kent.windbook.core.model.EffectiveDisplay(dev.kent.windbook.core.model.PdfLayout.PAGED,false))}
        compose.onNodeWithContentDescription("Reading appearance (Aa)").assertDoesNotExist()
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
    private fun sheet(book: dev.kent.windbook.core.model.ReaderTypography,onChange: ((dev.kent.windbook.core.model.ReaderTypography)->dev.kent.windbook.core.model.ReaderTypography,Boolean)->Unit={_,_->}) {
        compose.setContent {ReaderTypographySheet(book,dev.kent.windbook.core.model.ReaderTypography(),{},onChange)}
    }
    @Test fun scopeSelectorShowsTheSelectedSegmentAndItsHint() {
        sheet(dev.kent.windbook.core.model.ReaderTypography())
        compose.onNodeWithText("This book").assertIsSelected()
        compose.onNodeWithText("All books").assertIsNotSelected()
        compose.onNodeWithText("Only this book changes.").assertIsDisplayed()
        compose.onNodeWithText("All books").performClick()
        compose.onNodeWithText("All books").assertIsSelected()
        compose.onNodeWithText("Every book changes, except books you set separately.").assertIsDisplayed()
    }
    @Test fun textSizeShowsItsNumberOrThePublisherDefault() {
        sheet(dev.kent.windbook.core.model.ReaderTypography(size=120))
        compose.onNodeWithText("Text size · 120%").assertExists()
    }
    @Test fun unsetTextSizeSaysPublisherDefault() {
        sheet(dev.kent.windbook.core.model.ReaderTypography())
        compose.onNodeWithText("Text size · Publisher default").assertExists()
    }
    @Test fun lineHeightResetIsOnlyOfferedWhenAValueIsSet() {
        var latest=dev.kent.windbook.core.model.ReaderTypography(lineHeight=1.6)
        sheet(latest) {edit,_->latest=edit(latest)}
        compose.onNodeWithText("Use the book's own line height").performScrollTo().performClick()
        assertEquals(0.0,latest.lineHeight!!,0.0)
    }
    @Test fun noLineHeightResetWhenUnset() {
        sheet(dev.kent.windbook.core.model.ReaderTypography())
        compose.onNodeWithText("Use the book's own line height").assertDoesNotExist()
    }
    @Test fun colourChipsCarrySwatches() {
        sheet(dev.kent.windbook.core.model.ReaderTypography())
        listOf("PAPER","SEPIA","DARK").forEach {compose.onNodeWithTag("swatch:$it",useUnmergedTree=true).assertExists()}
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
