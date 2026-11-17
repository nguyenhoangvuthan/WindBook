package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryTest {
    private fun item(
        id: String,
        title: String,
        author: String? = null,
        status: ReadingStatus = ReadingStatus.TO_READ,
        addedAt: Long = 0,
        lastOpenedAt: Long? = null,
        progression: Double = 0.0,
        format: BookFormat = BookFormat.EPUB,
    ) = LibraryItem(
        Book(id, title, author, format, "/b/$id.${format.extension}", 1, "sha$id", null, status, addedAt, lastOpenedAt),
        progression,
    )

    private val dune = item("1", "Dune", "Frank Herbert", ReadingStatus.READING, addedAt = 10, lastOpenedAt = 500, progression = 0.4)
    private val viet = item("2", "Dế Mèn Phiêu Lưu Ký", "Tô Hoài", ReadingStatus.FINISHED, addedAt = 30, lastOpenedAt = 100, progression = 1.0)
    private val code = item("3", "clean code", null, ReadingStatus.TO_READ, addedAt = 20)
    private val all = listOf(code, viet, dune)

    @Test fun formatFilterKeepsOnlySelectedFormatsAndCombinesWithStatusAndText() {
        val epub = item("e", "Dune", status = ReadingStatus.READING, format = BookFormat.EPUB)
        val pdf = item("p", "Dune Notes", status = ReadingStatus.READING, format = BookFormat.PDF)
        val pdfDone = item("d", "Dune Atlas", status = ReadingStatus.FINISHED, format = BookFormat.PDF)
        val txt = item("t", "Plain", status = ReadingStatus.READING, format = BookFormat.TXT)
        val library = listOf(epub, pdf, pdfDone, txt)
        fun ids(q: LibraryQuery) = q.apply(library).map { it.book.id }.sorted()

        assertEquals(listOf("d", "e", "p", "t"), ids(LibraryQuery()))
        assertEquals(listOf("d", "p"), ids(LibraryQuery(formats = setOf(BookFormat.PDF))))
        assertEquals(listOf("e", "t"), ids(LibraryQuery(formats = setOf(BookFormat.EPUB, BookFormat.TXT))))
        assertEquals(listOf("p"), ids(LibraryQuery(filter = StatusFilter.READING, formats = setOf(BookFormat.PDF))))
        assertEquals(listOf("d", "p"), ids(LibraryQuery(text = "dune", formats = setOf(BookFormat.PDF))))
    }

    @Test fun recentSortsByLastOpenedThenNeverOpenedLast() =
        assertEquals(listOf("1", "2", "3"), LibraryQuery(sort = LibrarySort.RECENT).apply(all).map { it.book.id })

    @Test fun titleSortIsCaseInsensitive() =
        assertEquals(listOf("3", "2", "1"), LibraryQuery(sort = LibrarySort.TITLE).apply(all).map { it.book.id })

    @Test fun authorSortPutsMissingAuthorLast() =
        assertEquals(listOf("1", "2", "3"), LibraryQuery(sort = LibrarySort.AUTHOR).apply(all).map { it.book.id })

    @Test fun addedSortNewestFirst() =
        assertEquals(listOf("2", "3", "1"), LibraryQuery(sort = LibrarySort.ADDED).apply(all).map { it.book.id })

    @Test fun progressSortHighestFirst() =
        assertEquals(listOf("2", "1", "3"), LibraryQuery(sort = LibrarySort.PROGRESS).apply(all).map { it.book.id })

    @Test fun searchIgnoresAccentsAndMatchesAuthor() {
        assertEquals(listOf("2"), LibraryQuery(text = "de men").apply(all).map { it.book.id })
        assertEquals(listOf("2"), LibraryQuery(text = "to hoai").apply(all).map { it.book.id })
    }

    @Test fun statusFilter() =
        assertEquals(listOf("1"), LibraryQuery(filter = StatusFilter.READING).apply(all).map { it.book.id })

    @Test fun continueReadingPicksMostRecentReadingBook() {
        assertEquals("1", continueReading(all)?.book?.id)
        assertNull(continueReading(listOf(code, viet)))
    }
}
