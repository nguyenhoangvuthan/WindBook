package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {
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
}
