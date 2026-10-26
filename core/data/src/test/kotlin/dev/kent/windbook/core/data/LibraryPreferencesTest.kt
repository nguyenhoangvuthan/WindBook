package dev.kent.windbook.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPreferencesTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun scanDefaultsPersistIndependentlyOfLibraryLayoutAndSort() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "scan.preferences_pb") }
        val prefs = LibraryPreferences(store)
        assertEquals(dev.kent.windbook.core.model.ScanOptions(), prefs.scanOptions.first())
        prefs.setScanOptions(dev.kent.windbook.core.model.ScanOptions(setOf(dev.kent.windbook.core.model.BookFormat.TXT), false))
        prefs.setSort(LibrarySort.TITLE)
        prefs.setLayout(LibraryLayout.LIST)
        val reopened = LibraryPreferences(store)
        assertEquals(dev.kent.windbook.core.model.ScanOptions(setOf(dev.kent.windbook.core.model.BookFormat.TXT), false), reopened.scanOptions.first())
        assertEquals(LibrarySort.TITLE, reopened.sort.first())
        reopened.setScanOptions(dev.kent.windbook.core.model.ScanOptions(emptySet(), true))
        assertEquals(emptySet<dev.kent.windbook.core.model.BookFormat>(), prefs.scanOptions.first().extensions)
    }

    @Test fun pausedFormatsCannotLeakIntoPersistedDefaults() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "paused.preferences_pb") }
        val prefs = LibraryPreferences(store)
        prefs.setScanOptions(dev.kent.windbook.core.model.ScanOptions(setOf(
            dev.kent.windbook.core.model.BookFormat.PDF, dev.kent.windbook.core.model.BookFormat.CBZ), false))
        assertEquals(dev.kent.windbook.core.model.ScanOptions(setOf(dev.kent.windbook.core.model.BookFormat.PDF), false), prefs.scanOptions.first())
    }

    @Test fun defaultsThenPersistedValues() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { java.io.File(tmp.root, "library.preferences_pb") }
        val prefs = LibraryPreferences(store)

        assertEquals(LibrarySort.RECENT, prefs.sort.first())
        assertEquals(LibraryLayout.GRID, prefs.layout.first())

        prefs.setSort(LibrarySort.TITLE)
        prefs.setLayout(LibraryLayout.LIST)

        assertEquals(LibrarySort.TITLE, prefs.sort.first())
        assertEquals(LibraryLayout.LIST, prefs.layout.first())
    }
}
