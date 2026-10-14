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

/**
 * Switches tabs. Library is always the root and at most one other tab sits above it, so
 * back from any tab returns to Library (Material guidance) and the stack never grows.
 */
fun MutableList<NavKey>.selectTab(tab: TopLevelTab) {
    if (lastOrNull() == tab.key) return
    while (size > 1) removeAt(lastIndex)
    if (tab != TopLevelTab.LIBRARY) add(tab.key)
}
