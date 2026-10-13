package dev.kent.windbook.reader

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderOverlayTest {
    @get:Rule val compose = createComposeRule()

    @Test fun visibleOverlayShowsTitleChapterProgressAndBack() {
        var backs = 0
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = "Book One", progression = 0.37,
                canMarkFinished = false, onMarkFinished = {}, onBack = { backs++ },
            )
        }
        compose.onNodeWithText("Dune").assertIsDisplayed()
        compose.onNodeWithText("Book One · 37%").assertIsDisplayed()
        compose.onNodeWithText("Mark as finished").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
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
        compose.onNodeWithText("Dune").assertDoesNotExist()
    }
}
