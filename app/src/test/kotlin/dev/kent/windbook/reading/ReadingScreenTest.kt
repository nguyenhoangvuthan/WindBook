package dev.kent.windbook.reading

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ReadingScreenTest {
    @get:Rule val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private var browse = 0

    private fun item(id: String, title: String) = LibraryItem(
        Book(id, title, "Author $id", BookFormat.EPUB, "/f/$id", 1, "sha-$id", null, ReadingStatus.READING, 0, 1),
        0.37,
    )

    @Test fun emptyStateLeadsToLibrary() {
        compose.setContent { ReadingScreen(ReadingUiState(loaded = true), { opened += it }, { browse++ }) }
        compose.onNodeWithText("No book in progress").assertIsDisplayed()
        compose.onNodeWithText("Go to library").performClick()
        assertEquals(1, browse)
    }

    @Test fun nothingIsShownBeforeTheLibraryLoads() {
        compose.setContent { ReadingScreen(ReadingUiState(), { opened += it }, { browse++ }) }
        compose.onNodeWithText("No book in progress").assertDoesNotExist()
    }

    @Test fun continueOpensTheCurrentBook() {
        val state = ReadingUiState(loaded = true, current = item("1", "Dune"), others = listOf(item("2", "Emma")))
        compose.setContent { ReadingScreen(state, { opened += it }, { browse++ }) }
        compose.onNodeWithText("Dune").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        assertEquals(listOf("1"), opened)
    }

    @Test fun otherBooksOpenFromTheList() {
        val state = ReadingUiState(loaded = true, current = item("1", "Dune"), others = listOf(item("2", "Emma")))
        compose.setContent { ReadingScreen(state, { opened += it }, { browse++ }) }
        compose.onNodeWithText("Emma").performClick()
        assertEquals(listOf("2"), opened)
    }

    @Config(qualifiers = "vi")
    @Test fun vietnameseTitle() {
        compose.setContent { ReadingScreen(ReadingUiState(loaded = true), { }, { }) }
        compose.onNodeWithText("Đang đọc").assertIsDisplayed()
        compose.onNodeWithText("Chưa có sách đang đọc").assertIsDisplayed()
    }
}
