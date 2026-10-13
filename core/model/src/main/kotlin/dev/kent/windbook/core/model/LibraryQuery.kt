package dev.kent.windbook.core.model

import java.text.Collator
import java.util.Locale

enum class LibraryLayout { GRID, LIST }

enum class StatusFilter {
    ALL, READING, TO_READ, FINISHED;

    fun matches(status: ReadingStatus): Boolean = when (this) {
        ALL -> true
        READING -> status == ReadingStatus.READING
        TO_READ -> status == ReadingStatus.TO_READ
        FINISHED -> status == ReadingStatus.FINISHED
    }
}

private val titleCollator: Comparator<String> =
    Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        .let { collator -> Comparator { a, b -> collator.compare(a, b) } }

enum class LibrarySort {
    RECENT, TITLE, AUTHOR, ADDED, PROGRESS;

    val comparator: Comparator<LibraryItem>
        get() = when (this) {
            RECENT -> compareByDescending<LibraryItem> { it.book.lastOpenedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.book.addedAt }
            TITLE -> compareBy(titleCollator) { it.book.title }
            AUTHOR -> compareBy<LibraryItem, String?>(nullsLast(titleCollator)) { it.book.author }
                .thenBy(titleCollator) { it.book.title }
            ADDED -> compareByDescending { it.book.addedAt }
            PROGRESS -> compareByDescending<LibraryItem> { it.progression }
                .thenBy(titleCollator) { it.book.title }
        }
}

data class LibraryQuery(
    val text: String = "",
    val sort: LibrarySort = LibrarySort.RECENT,
    val filter: StatusFilter = StatusFilter.ALL,
) {
    fun apply(items: List<LibraryItem>): List<LibraryItem> {
        val needle = SearchText.normalize(text.trim())
        return items
            .filter { filter.matches(it.book.status) }
            .filter { needle.isEmpty() || it.matches(needle) }
            .sortedWith(sort.comparator)
    }

    private fun LibraryItem.matches(needle: String): Boolean =
        SearchText.normalize(book.title).contains(needle) ||
            book.author?.let { SearchText.normalize(it).contains(needle) } == true
}

/** The book shown in the "Continue reading" card: the most recently opened READING book. */
fun continueReading(items: List<LibraryItem>): LibraryItem? =
    items.filter { it.book.status == ReadingStatus.READING && it.book.lastOpenedAt != null }
        .maxByOrNull { it.book.lastOpenedAt!! }
