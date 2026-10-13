package dev.kent.windbook.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.libraryDataStore: DataStore<Preferences> by preferencesDataStore(name = "library")

class LibraryPreferences(private val dataStore: DataStore<Preferences>) {

    val sort: Flow<LibrarySort> = dataStore.data.map { prefs ->
        prefs[SORT]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT
    }

    val layout: Flow<LibraryLayout> = dataStore.data.map { prefs ->
        prefs[LAYOUT]?.let { runCatching { LibraryLayout.valueOf(it) }.getOrNull() } ?: LibraryLayout.GRID
    }

    suspend fun setSort(sort: LibrarySort) {
        dataStore.edit { it[SORT] = sort.name }
    }

    suspend fun setLayout(layout: LibraryLayout) {
        dataStore.edit { it[LAYOUT] = layout.name }
    }

    private companion object {
        val SORT = stringPreferencesKey("sort")
        val LAYOUT = stringPreferencesKey("layout")
    }
}
