package dev.kent.windbook.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LibraryScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val statuses = mutableListOf<Pair<String, ReadingStatus>>()
    private var importClicks = 0

    private val actions = LibraryActions(
        onOpenBook = { opened += it },
        onImportClick = { importClicks++ },
        onQueryChange = {}, onFilterChange = {}, onSortChange = {}, onLayoutChange = {},
        onSetStatus = { id, status -> statuses += id to status },
        onDelete = {},
    )

    private val dune = LibraryItem(
        Book("1", "Dune", "Frank Herbert", BookFormat.EPUB, "/f", 1, "s", null, ReadingStatus.READING, 1, 5),
        0.42,
    )

    @Test fun emptyLibraryShowsAddBooks() {
        compose.setContent { LibraryScreen(LibraryUiState(isLibraryEmpty = true), actions) }
        compose.onNodeWithText("Add books").assertIsDisplayed().performClick()
        assertEquals(1, importClicks)
    }

    @Config(qualifiers = "vi")
    @Test fun emptyLibraryShowsVietnameseAddBooks() {
        compose.setContent { LibraryScreen(LibraryUiState(isLibraryEmpty = true), actions) }
        compose.onNodeWithText("Thêm sách").assertIsDisplayed()
    }

    @Test fun continueReadingCardOpensBook() {
        compose.setContent {
            LibraryScreen(LibraryUiState(items = listOf(dune), continueReading = dune, isLibraryEmpty = false), actions)
        }
        compose.onNodeWithText("Continue reading").assertIsDisplayed()
        // "42%" appears in the card and in the grid cell below it.
        compose.onAllNodesWithText("42%").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Continue reading").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun longPressOffersStatusChange() {
        compose.setContent {
            LibraryScreen(LibraryUiState(items = listOf(dune), layout = LibraryLayout.LIST, isLibraryEmpty = false), actions)
        }
        compose.onNodeWithText("Dune").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }
}
