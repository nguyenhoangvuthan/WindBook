package dev.kent.windbook.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Opens a book without stacking duplicate readers: a double tap must not create two reader
 * entries, because the lower one would later re-save its stale position over the newer one.
 */
fun MutableList<NavKey>.openReader(bookId: String) {
    val key = ReaderKey(bookId)
    if (lastOrNull() == key) return
    removeAll { it == key }
    add(key)
}
