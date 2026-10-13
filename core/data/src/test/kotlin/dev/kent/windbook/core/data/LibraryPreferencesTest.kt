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
