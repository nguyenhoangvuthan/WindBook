package dev.kent.windbook.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.data.folders.availableFormats
import androidx.datastore.preferences.preferencesDataStore
import dev.kent.windbook.core.model.ScanOptions
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

    val scanOptions: Flow<ScanOptions> = dataStore.data.map { prefs ->
        val extensions = prefs[SCAN_EXTENSIONS]?.mapNotNull { name ->
            BookFormat.entries.firstOrNull { it.name == name }
        }?.toSet() ?: ScanOptions().extensions
        ScanOptions(extensions, prefs[SCAN_RECURSIVE] ?: true).availableFormats()
    }

    suspend fun setScanOptions(options: ScanOptions) {
        val available = options.availableFormats()
        dataStore.edit {
            it[SCAN_EXTENSIONS] = available.extensions.map { format -> format.name }.toSet()
            it[SCAN_RECURSIVE] = available.recursive
        }
    }

    private companion object {
        val SCAN_EXTENSIONS = stringSetPreferencesKey("scan_extensions")
        val SCAN_RECURSIVE = booleanPreferencesKey("scan_recursive")
        val SORT = stringPreferencesKey("sort")
        val LAYOUT = stringPreferencesKey("layout")
    }
}
