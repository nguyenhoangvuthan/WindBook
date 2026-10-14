package dev.kent.windbook.library

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.library.components.LIBRARY_SEARCH_TAG
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
    private val queries = mutableListOf<String>()
    private val sorts = mutableListOf<LibrarySort>()
    private val layouts = mutableListOf<LibraryLayout>()
    private var importClicks = 0

    private val actions = LibraryActions(
        onOpenBook = { opened += it },
        onImportClick = { importClicks++ },
        onQueryChange = { queries += it },
        onFilterChange = {},
        onSortChange = { sorts += it },
        onLayoutChange = { layouts += it },
        onSetStatus = { id, status -> statuses += id to status },
        onDelete = {},
    )

    private val dune = LibraryItem(
        Book("1", "Dune", "Frank Herbert", BookFormat.EPUB, "/f", 1, "s", null, ReadingStatus.READING, 1, 5),
        0.42,
    )

    private fun library(vararg items: LibraryItem, layout: LibraryLayout = LibraryLayout.GRID, continueReading: LibraryItem? = null) =
        LibraryUiState(
            items = items.toList(),
            continueReading = continueReading,
            layout = layout,
            bookCount = items.size,
            isLibraryEmpty = items.isEmpty(),
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

    @Test fun headerShowsTotalBookCount() {
        compose.setContent { LibraryScreen(library(dune).copy(bookCount = 12), actions) }
        compose.onNodeWithText("Library").assertIsDisplayed()
        compose.onNodeWithText("12 books").assertIsDisplayed()
    }

    @Config(qualifiers = "vi")
    @Test fun headerCountInVietnamese() {
        compose.setContent { LibraryScreen(library(dune).copy(bookCount = 12), actions) }
        compose.onNodeWithText("12 cuốn").assertIsDisplayed()
    }

    @Test fun searchFieldIsAlwaysVisibleAndReportsTyping() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithTag(LIBRARY_SEARCH_TAG).assertIsDisplayed().performTextInput("dun")
        assertEquals("dun", queries.last())
    }

    @Test fun sortPillOpensMenuAndSelects() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithText("Sort").performClick()
        compose.onNodeWithContentDescription("Selected").assertIsDisplayed() // RECENT is the current sort
        compose.onNodeWithText("Title").performClick()
        assertEquals(listOf(LibrarySort.TITLE), sorts)
    }

    @Test fun continueReadingCardOpensBook() {
        compose.setContent { LibraryScreen(library(dune, continueReading = dune), actions) }
        compose.onNodeWithText("CONTINUE READING").assertIsDisplayed()
        compose.onNodeWithText("42%").assertIsDisplayed()
        compose.onNodeWithText("CONTINUE READING").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun moreButtonOnCoverOpensBookMenu() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithContentDescription("More options for Dune").performClick()
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }

    @Test fun longPressStillOffersStatusChange() {
        compose.setContent { LibraryScreen(library(dune, layout = LibraryLayout.LIST), actions) }
        compose.onNodeWithText("Dune").performTouchInput { longClick() }
        compose.onNodeWithText("Mark as finished").performClick()
        assertEquals(listOf("1" to ReadingStatus.FINISHED), statuses)
    }

    @Test fun finishedBookShowsFinishedCaption() {
        val done = dune.copy(book = dune.book.copy(status = ReadingStatus.FINISHED))
        compose.setContent { LibraryScreen(library(done), actions) }
        // "Finished" is both the filter pill and the caption that replaces the author.
        compose.onAllNodesWithText("Finished").assertCountEquals(2)
        compose.onNodeWithText("Frank Herbert").assertDoesNotExist()
    }

    @Test fun fabAddsBooks() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithContentDescription("Add books").performClick()
        assertEquals(1, importClicks)
    }

    @Test fun fabWhileImportingSaysImporting() {
        compose.setContent { LibraryScreen(library(dune).copy(isImporting = true), actions) }
        compose.onNodeWithContentDescription("Importing…").assertIsDisplayed()
    }

    @Test fun sortIsAPlainButtonNotASelectableChip() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithText("Sort")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithText("All").assertIsSelected()
    }

    @Test fun titleIsAHeadingAndTheSeparatorIsSilent() {
        compose.setContent { LibraryScreen(library(dune).copy(bookCount = 12), actions) }
        compose.onNode(isHeading() and hasText("Library")).assertExists()
        compose.onNodeWithText("│").assertDoesNotExist()
    }

    @Test fun layoutToggleSwitchesLayout() {
        compose.setContent { LibraryScreen(library(dune), actions) }
        compose.onNodeWithContentDescription("Change layout").performClick()
        assertEquals(listOf(LibraryLayout.LIST), layouts)
    }
}
