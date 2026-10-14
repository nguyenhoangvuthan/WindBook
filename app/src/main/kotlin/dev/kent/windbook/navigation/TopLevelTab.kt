package dev.kent.windbook.navigation

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import dev.kent.windbook.R

/** The four bottom-bar destinations. Order here is the order on screen. */
enum class TopLevelTab(val key: NavKey, @StringRes val label: Int) {
    LIBRARY(LibraryKey, R.string.tab_library),
    READING(ReadingKey, R.string.tab_reading),
    NOTES(NotesKey, R.string.tab_notes),
    SETTINGS(SettingsKey, R.string.tab_settings),
}

/** The tab this key is the root of, or null for screens that hide the tab bar (the reader). */
fun NavKey.topLevelTab(): TopLevelTab? = TopLevelTab.entries.firstOrNull { it.key == this }
