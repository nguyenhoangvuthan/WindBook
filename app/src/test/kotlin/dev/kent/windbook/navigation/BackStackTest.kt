package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {
    @Test fun repeatedAnnotationNavigationCreatesFreshRequestAndKeepsOneReaderPerBook() {
        val stack=mutableListOf<NavKey>(LibraryKey,ReaderKey("a"),NotesKey)
        stack.openReader("a","note")
        val first=stack.last() as ReaderKey
        assertEquals("note",first.annotationId)
        stack.openReader("a","note")
        val second=stack.last() as ReaderKey
        org.junit.Assert.assertNotEquals(first.revealRequestId,second.revealRequestId)
        assertEquals(1,stack.filterIsInstance<ReaderKey>().count {it.bookId=="a"})
    }

    @Test fun folderResultsOpenReaderByStableIdAndReturnToFolder() {
        val stack = mutableListOf<NavKey>(LibraryKey, FolderKey("root"))
        stack.openReader("book")
        assertEquals(ReaderKey("book"), stack.last())
        stack.removeAt(stack.lastIndex)
        assertEquals(FolderKey("root"), stack.last())
    }

    @Test fun doubleTapOpensTheReaderOnce() {
        val stack = mutableListOf<NavKey>(LibraryKey)
        stack.openReader("a")
        stack.openReader("a")
        assertEquals(listOf(LibraryKey, ReaderKey("a")), stack)
    }

    @Test fun reopeningABookAlreadyInTheStackMovesItToTheTop() {
        val stack = mutableListOf<NavKey>(LibraryKey, ReaderKey("a"), ReaderKey("b"))
        stack.openReader("a")
        assertEquals(listOf(LibraryKey, ReaderKey("b"), ReaderKey("a")), stack)
    }

    @Test fun selectingATabPutsItAboveLibrary() {
        val stack = mutableListOf<NavKey>(LibraryKey)
        stack.selectTab(TopLevelTab.SETTINGS)
        assertEquals(listOf(LibraryKey, SettingsKey), stack)
    }

    @Test fun switchingTabsKeepsOnlyOneAboveLibrary() {
        val stack = mutableListOf<NavKey>(LibraryKey, ReadingKey)
        stack.selectTab(TopLevelTab.NOTES)
        assertEquals(listOf(LibraryKey, NotesKey), stack)
    }

    @Test fun selectingLibraryReturnsToTheRoot() {
        val stack = mutableListOf<NavKey>(LibraryKey, SettingsKey)
        stack.selectTab(TopLevelTab.LIBRARY)
        assertEquals(listOf(LibraryKey), stack)
    }

    @Test fun reselectingTheCurrentTabDoesNothing() {
        val stack = mutableListOf<NavKey>(LibraryKey, SettingsKey)
        stack.selectTab(TopLevelTab.SETTINGS)
        assertEquals(listOf(LibraryKey, SettingsKey), stack)
    }

    @Test fun readerOpenedFromReadingTabReturnsToIt() {
        val stack = mutableListOf<NavKey>(LibraryKey)
        stack.selectTab(TopLevelTab.READING)
        stack.openReader("a")
        stack.removeAt(stack.lastIndex) // system back from the reader
        assertEquals(ReadingKey, stack.last())
    }

    @Test fun tabBarShowsOnlyOnTopLevelScreens() {
        assertEquals(TopLevelTab.SETTINGS, SettingsKey.topLevelTab())
        assertEquals(TopLevelTab.LIBRARY, LibraryKey.topLevelTab())
        assertEquals(null, ReaderKey("a").topLevelTab())
    }
}
