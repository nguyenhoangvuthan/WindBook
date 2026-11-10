package dev.kent.windbook.library

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.kent.windbook.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MinimalLibraryTest {
    @get:Rule val compose=createComposeRule()
    private val book=LibraryItem(Book("one","Synthetic title","Synthetic author",BookFormat.EPUB,"/f",1,"sha",null,ReadingStatus.READING,1,2),.4)
    private fun actions(files: ()->Unit={},folders: ()->Unit={},open: (String)->Unit={})=LibraryActions(open,files,{}, {}, {}, {}, {_,_->}, {},folders)
    @Test fun plusOffersFilesAndFoldersWithoutOpeningEitherEarly() {
        var files=0;var folders=0
        compose.setContent {LibraryScreen(LibraryUiState(items=listOf(book),isLibraryEmpty=false),actions({files++},{folders++}))}
        compose.onNodeWithContentDescription("Add books").performClick()
        compose.onNodeWithText("Choose files").assertIsDisplayed()
        compose.onNodeWithText("Choose folder").assertIsDisplayed()
        assertEquals(0,files);assertEquals(0,folders)
        compose.onNodeWithText("Choose folder").performClick()
        assertEquals(0,files);assertEquals(1,folders)
    }
    @Test fun cancellingAddChoiceDoesNotDispatchEitherPicker() {
        var files=0;var folders=0
        compose.setContent {LibraryScreen(LibraryUiState(),actions({files++},{folders++}))}
        compose.onNodeWithText("Add books").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,files);assertEquals(0,folders)
        compose.onNodeWithText("Choose folder").assertDoesNotExist()
    }
    @Test fun emptyLibraryUsesTheSameFileAndFolderChoice() {
        var files=0
        compose.setContent {LibraryScreen(LibraryUiState(),actions(files={files++}))}
        compose.onNodeWithText("Add books").performClick()
        compose.onNodeWithText("Choose files").performClick()
        assertEquals(1,files)
    }
    @Test fun gridHidesCaptionsButAccessibleCoverStillOpensTheBook() {
        val opened=mutableListOf<String>()
        compose.setContent {LibraryScreen(LibraryUiState(items=listOf(book),continueReading=book,isLibraryEmpty=false),actions(open={opened+=it}))}
        compose.onNodeWithText("Synthetic title").assertDoesNotExist()
        compose.onNodeWithText("Synthetic author").assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Synthetic title, Synthetic author").onLast().performClick()
        assertEquals(listOf("one"),opened)
    }
}
