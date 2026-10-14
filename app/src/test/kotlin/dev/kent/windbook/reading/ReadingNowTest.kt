package dev.kent.windbook.reading

import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingNowTest {
    private fun item(id: String, status: ReadingStatus, opened: Long?, added: Long = 0) = LibraryItem(
        Book(id, "Book $id", null, BookFormat.EPUB, "/f/$id", 1, "sha-$id", null, status, added, opened),
        0.5,
    )

    @Test fun onlyReadingBooksNewestFirst() {
        val state = readingNow(
            listOf(
                item("a", ReadingStatus.READING, opened = 10),
                item("b", ReadingStatus.FINISHED, opened = 99),
                item("c", ReadingStatus.READING, opened = 30),
                item("d", ReadingStatus.TO_READ, opened = 50),
            ),
        )
        assertTrue(state.loaded)
        assertEquals("c", state.current?.book?.id)
        assertEquals(listOf("a"), state.others.map { it.book.id })
    }

    @Test fun neverOpenedReadingBookSortsLast() {
        val state = readingNow(
            listOf(
                item("x", ReadingStatus.READING, opened = null, added = 500),
                item("y", ReadingStatus.READING, opened = 1),
            ),
        )
        assertEquals("y", state.current?.book?.id)
        assertEquals(listOf("x"), state.others.map { it.book.id })
    }

    @Test fun noReadingBooksIsEmptyButLoaded() {
        val state = readingNow(listOf(item("a", ReadingStatus.TO_READ, opened = 1)))
        assertTrue(state.loaded)
        assertNull(state.current)
        assertTrue(state.others.isEmpty())
    }
}
