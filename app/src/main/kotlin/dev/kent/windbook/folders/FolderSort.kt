package dev.kent.windbook.folders

import dev.kent.windbook.core.data.folders.FolderEntry
import java.text.Collator
import java.util.Locale

enum class FolderSort {
    TITLE_ASC, TITLE_DESC, ADDED_NEWEST, ADDED_OLDEST;

    val comparator: Comparator<FolderEntry>
        get() {
            val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
            val titles = Comparator<FolderEntry> { a, b -> collator.compare(a.title, b.title) }
            val primary: Comparator<FolderEntry> = when (this) {
                TITLE_ASC -> titles
                TITLE_DESC -> titles.reversed()
                ADDED_NEWEST -> compareByDescending { it.addedAt }
                ADDED_OLDEST -> compareBy { it.addedAt }
            }
            return primary.thenBy { it.bookId }.thenBy { it.sourceId }
        }
}
