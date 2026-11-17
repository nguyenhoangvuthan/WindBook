package dev.kent.windbook.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kent.windbook.core.model.EffectiveDisplay
import dev.kent.windbook.core.model.PdfLayout
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ReaderOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleOverlayShowsTitleMinimalProgressAndBack() {
        var backs = 0
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = "Book One", progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = { backs++ },
            )
        }
        compose.onNodeWithText("DUNE").assertIsDisplayed()
        // Chapter and percentage are two separate texts, never one "chapter · percent" caption.
        compose.onNodeWithText("Book One").assertIsDisplayed()
        compose.onNodeWithText("37%").assertIsDisplayed()
        compose.onNodeWithText("Book One · 37%").assertDoesNotExist()
        compose.onNodeWithText("Mark as finished").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test fun sliderKeepsASideGutter() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = "Book One", progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = {}, onSeek = {},
                seekFraction = 0.0,
            )
        }
        // At 0 % the thumb must stay clear of the system back-gesture zone at the screen edge.
        // (The slider's semantic bounds also cover touch slop, so measure the thumb itself.)
        val left = compose.onNodeWithTag(READER_SLIDER_THUMB_TAG, useUnmergedTree = true).getBoundsInRoot().left
        assertTrue("thumb starts at $left", left >= 16.dp)
    }

    @Test fun blankChapterShowsOnlyThePercentage() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = "   ", progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("37%").assertIsDisplayed()
    }

    @Test fun longVietnameseChapterKeepsPercentageVisible() {
        val long = "Chương mười hai: Quyết định của người đàn ông đã đứng một mình trước ngã ba đường " .repeat(4)
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = long, progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("37%").assertIsDisplayed()
    }

    @Test fun pdfFooterKeepsThePageButton() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = null, progression = 0.0,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.PAGED, false), pdfPage = 3, pdfPageCount = 42,
            )
        }
        compose.onNodeWithText("Page 3 / 42").assertIsDisplayed()
    }

    @Test fun nearEndOffersMarkFinished() {
        var marked = 0
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = null, progression = 0.995,
                canMarkFinished = true, onMarkFinished = { marked++ }, onBack = {},
            )
        }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(1, marked)
    }

    @Test fun hiddenOverlayShowsNothing() {
        compose.setContent {
            ReaderOverlay(
                visible = false, title = "Dune", chapterTitle = null, progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("DUNE").assertDoesNotExist()
    }

    @Test fun titleIsUppercasedKeepingVietnameseDiacritics() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Đắc nhân tâm", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("ĐẮC NHÂN TÂM").assertIsDisplayed()
        compose.onNodeWithText("10%").assertIsDisplayed()
    }

    @Config(qualifiers = "tr")
    @Test fun titleIsUppercasedWithTheAppLanguageRules() {
        // Turkish dotted i: locale-less uppercasing gives "ISTANBUL", the correct form is "İSTANBUL".
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "istanbul", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithText("İSTANBUL").assertIsDisplayed()
    }

    @Test fun epubHasNoViewButton() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithContentDescription("View").assertDoesNotExist()
    }

    @Test fun pdfViewMenuSwitchesLayout() {
        val layouts = mutableListOf<PdfLayout>()
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
                onPdfLayoutChange = { layouts += it },
            )
        }
        compose.onNodeWithContentDescription("More reading options").performClick()
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithText("Page by page").performClick()
        assertEquals(listOf(PdfLayout.PAGED), layouts)
    }

    @Test fun cropItemShowsAndTogglesState() {
        val crops = mutableListOf<Boolean>()
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
                onCropMarginsChange = { crops += it },
            )
        }
        compose.onNodeWithContentDescription("More reading options").performClick()
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithText("Crop margins").assertIsOn().performClick()
        assertEquals(listOf(false), crops)
    }

    @Test fun pdfViewMenuOffersOriginalPageLayoutsWithoutReflow() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
            )
        }
        compose.onNodeWithContentDescription("More reading options").performClick()
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithText("Vertical scroll").assertIsDisplayed()
        compose.onNodeWithText("Page by page").assertIsDisplayed()
        compose.onNodeWithText("Reflow").assertDoesNotExist()
    }
    @Test fun fitPageActionRemainsAvailableWhenAlreadyPagedAndFitted() {
        var fits = 0
        compose.setContent {
            ReaderOverlay(visible = true, title = "Doc", chapterTitle = null, progression = .4,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.PAGED, false), onFitPdfPage = { fits++ })
        }
        repeat(2) {
            compose.onNodeWithContentDescription("More reading options").performClick()
        compose.onNodeWithContentDescription("View").performClick()
            compose.onNodeWithText("Fit page").performClick()
        }
        assertEquals(2, fits)
    }

}
