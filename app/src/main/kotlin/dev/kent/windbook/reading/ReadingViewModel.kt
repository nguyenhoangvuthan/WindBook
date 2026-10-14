package dev.kent.windbook.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** [loaded] is false until the library has been read once, so the empty state never flashes. */
data class ReadingUiState(
    val loaded: Boolean = false,
    val current: LibraryItem? = null,
    val others: List<LibraryItem> = emptyList(),
)

/** READING books, most recently opened first; books never opened go last, newest added first. */
internal fun readingNow(all: List<LibraryItem>): ReadingUiState {
    val reading = all
        .filter { it.book.status == ReadingStatus.READING }
        .sortedWith(
            compareByDescending<LibraryItem> { it.book.lastOpenedAt ?: Long.MIN_VALUE }
                .thenByDescending { it.book.addedAt },
        )
    return ReadingUiState(loaded = true, current = reading.firstOrNull(), others = reading.drop(1))
}

@HiltViewModel
class ReadingViewModel @Inject constructor(repository: LibraryRepository) : ViewModel() {
    val state: StateFlow<ReadingUiState> = repository.observeLibrary()
        .map(::readingNow)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingUiState())
}
