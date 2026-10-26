package dev.kent.windbook.folders

import dev.kent.windbook.core.data.folders.FolderEntry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.SourceAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderSortTest {
    private fun entry(id: String, title: String, added: Long) = FolderEntry(
        id, "source-$id", "$id.pdf", "$id.pdf", BookFormat.PDF, SourceAvailability.AVAILABLE,
        title = title, addedAt = added)

    @Test fun titleAndAddedOrdersHaveStableTiesIndependentOfInputOrder() {
        val a = entry("a", "Zulu", 10)
        val b = entry("b", "Alpha", 20)
        val c = entry("c", "Alpha", 20)
        val input = listOf(c, a, b)
        fun ids(sort: FolderSort) = input.sortedWith(sort.comparator).map { it.bookId }
        assertEquals(listOf("b", "c", "a"), ids(FolderSort.TITLE_ASC))
        assertEquals(listOf("a", "b", "c"), ids(FolderSort.TITLE_DESC))
        assertEquals(listOf("b", "c", "a"), ids(FolderSort.ADDED_NEWEST))
        assertEquals(listOf("a", "b", "c"), ids(FolderSort.ADDED_OLDEST))
        FolderSort.entries.forEach { sort ->
            assertEquals(ids(sort), input.reversed().sortedWith(sort.comparator).map { it.bookId })
        }
    }
}
