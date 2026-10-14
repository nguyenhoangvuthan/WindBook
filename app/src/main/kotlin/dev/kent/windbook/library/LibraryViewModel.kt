package dev.kent.windbook.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.data.importer.ImportResult
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibraryQuery
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.core.model.continueReading
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val items: List<LibraryItem> = emptyList(),
    val continueReading: LibraryItem? = null,
    val query: String = "",
    val filter: StatusFilter = StatusFilter.ALL,
    val sort: LibrarySort = LibrarySort.RECENT,
    val layout: LibraryLayout = LibraryLayout.GRID,
    val isImporting: Boolean = false,
    val isLibraryEmpty: Boolean = true,
    /** Every book in the library, ignoring search and filter (header count). */
    val bookCount: Int = 0,
)

sealed interface LibraryMessage {
    data class Imported(val count: Int) : LibraryMessage
    data class AlreadyInLibrary(val bookId: String) : LibraryMessage
    data class ImportFailed(val name: String?, val error: ImportError) : LibraryMessage
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repository: LibraryRepository,
    private val preferences: LibraryPreferences,
    private val importer: BookImporter,
    private val sources: ImportSourceFactory,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StatusFilter.ALL)
    private val importing = MutableStateFlow(false)
    private val _messages = Channel<LibraryMessage>(Channel.BUFFERED)

    val messages: Flow<LibraryMessage> = _messages.receiveAsFlow()

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeLibrary(),
        query,
        filter,
        preferences.sort,
        preferences.layout,
    ) { all, text, statusFilter, sort, layout ->
        LibraryUiState(
            items = LibraryQuery(text, sort, statusFilter).apply(all),
            continueReading = continueReading(all),
            query = text,
            filter = statusFilter,
            sort = sort,
            layout = layout,
            isLibraryEmpty = all.isEmpty(),
            bookCount = all.size,
        )
    }.combine(importing) { state, busy -> state.copy(isImporting = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(text: String) = query.update { text }

    fun onFilterChange(value: StatusFilter) = filter.update { value }

    fun onSortChange(sort: LibrarySort) {
        viewModelScope.launch { preferences.setSort(sort) }
    }

    fun onLayoutChange(layout: LibraryLayout) {
        viewModelScope.launch { preferences.setLayout(layout) }
    }

    fun import(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            var imported = 0
            try {
                for (uri in uris) {
                    val source = try {
                        sources.create(uri)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // One unreadable file must not stop the rest of a multi-file import.
                        _messages.send(LibraryMessage.ImportFailed(null, ImportError.READ_FAILED))
                        continue
                    }
                    when (val result = importer.import(source)) {
                        is ImportResult.Imported -> imported++
                        is ImportResult.Duplicate -> _messages.send(LibraryMessage.AlreadyInLibrary(result.bookId))
                        is ImportResult.Failed -> _messages.send(LibraryMessage.ImportFailed(source.displayName, result.error))
                    }
                }
                if (imported > 0) _messages.send(LibraryMessage.Imported(imported))
            } finally {
                importing.value = false
            }
        }
    }

    fun setStatus(bookId: String, status: ReadingStatus) {
        viewModelScope.launch { repository.setStatus(bookId, status) }
    }

    fun delete(bookId: String) {
        viewModelScope.launch { repository.delete(bookId) }
    }
}
