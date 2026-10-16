package dev.kent.windbook.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.EffectiveDisplay
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.resolve
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ReaderError { NOT_FOUND, FILE_MISSING, CANNOT_OPEN }

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(
        val session: ReaderSession,
        val title: String,
        val initialLocatorJson: String?,
    ) : ReaderUiState
    data class Error(val error: ReaderError) : ReaderUiState
}

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val library: LibraryRepository,
    private val opener: BookOpener,
    private val displayPrefs: DisplayPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private val _progression = MutableStateFlow(0.0)
    val progression: StateFlow<Double> = _progression.asStateFlow()

    private val _chapterTitle = MutableStateFlow<String?>(null)
    val chapterTitle: StateFlow<String?> = _chapterTitle.asStateFlow()

    private val _overlayVisible = MutableStateFlow(false)
    val overlayVisible: StateFlow<Boolean> = _overlayVisible.asStateFlow()

    private val _pdfDisplay = MutableStateFlow<EffectiveDisplay?>(null)

    /** How a PDF is shown (null for EPUB or while loading). */
    val pdfDisplay: StateFlow<EffectiveDisplay?> = _pdfDisplay.asStateFlow()

    private var currentPrefs = BookDisplayPrefs()

    private val finished = MutableStateFlow(false)

    /** True near the end of a book that isn't marked finished yet (spec F1.6). */
    val canMarkFinished: StateFlow<Boolean> = combine(_progression, finished) { progression, isFinished ->
        progression >= FINISH_THRESHOLD && !isFinished
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Where a re-created reader (rotation, process death) must reopen; updated on every page change. */
    @Volatile
    var latestLocatorJson: String? = null
        private set

    /** Keeps only the newest pending locator; the writer always persists the latest one. */
    private val locatorUpdates = MutableSharedFlow<Pair<String, Double>>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private var session: ReaderSession? = null

    init {
        viewModelScope.launch { open() }
        viewModelScope.launch {
            locatorUpdates.collect { (json, progression) ->
                library.saveProgress(bookId, json, progression)
            }
        }
    }

    private suspend fun open() {
        val book = library.book(bookId) ?: run {
            _state.value = ReaderUiState.Error(ReaderError.NOT_FOUND)
            return
        }
        val file = File(book.filePath)
        if (!file.exists()) {
            _state.value = ReaderUiState.Error(ReaderError.FILE_MISSING)
            return
        }
        val savedLocator = library.savedLocator(bookId)
        finished.value = book.status == ReadingStatus.FINISHED
        opener.open(file, book.format)
            .onSuccess { opened ->
                session = opened
                latestLocatorJson = savedLocator
                library.markOpened(bookId)
                if (opened is PdfSession) {
                    // A broken page while sizing the first pages means the book can't be shown, not a crash.
                    val firstPages = try {
                        opened.firstPageSizes()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        opened.close()
                        session = null
                        _state.value = ReaderUiState.Error(ReaderError.CANNOT_OPEN)
                        return@onSuccess
                    }
                    viewModelScope.launch {
                        displayPrefs.observe(bookId).collect { prefs ->
                            currentPrefs = prefs
                            _pdfDisplay.value = prefs.resolve(firstPages)
                        }
                    }
                }
                _state.value = ReaderUiState.Ready(opened, book.title, savedLocator)
            }
            .onFailure { _state.value = ReaderUiState.Error(ReaderError.CANNOT_OPEN) }
    }

    fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
        latestLocatorJson = locatorJson
        _progression.value = progression
        _chapterTitle.value = chapterTitle
        locatorUpdates.tryEmit(locatorJson to progression)
    }

    fun toggleOverlay() = _overlayVisible.update { !it }

    fun setPdfLayout(layout: PdfLayout) = updatePrefs { it.copy(pdfLayout = layout) }

    fun setCropMargins(enabled: Boolean) = updatePrefs { it.copy(cropMargins = enabled) }

    fun setPdfZoom(zoom: Float, zoomX: Float) = updatePrefs { it.copy(zoom = zoom, zoomX = zoomX) }

    private fun updatePrefs(change: (BookDisplayPrefs) -> BookDisplayPrefs) {
        val next = change(currentPrefs)
        viewModelScope.launch { displayPrefs.set(bookId, next) }
    }

    /** PDF pages are 0-based; progress uses page / pageCount so the last page reaches 100 %. */
    fun onPdfPageChanged(page: Int) {
        val pdf = session as? PdfSession ?: return
        onLocatorChanged(
            locatorJson = pdf.locatorJsonFor(page),
            progression = readingProgression(position = page + 1, positionCount = pdf.pageCount, totalProgression = null),
            chapterTitle = null,
        )
    }

    /** Where a (re-)created PDF view starts: the newest position, else the saved one. */
    fun initialPdfPage(): Int = (session as? PdfSession)?.pageOf(latestLocatorJson) ?: 0

    fun markFinished() {
        finished.value = true
        viewModelScope.launch { library.setStatus(bookId, ReadingStatus.FINISHED) }
    }

    /** Used from the "file missing" error screen. */
    fun removeBook(onRemoved: () -> Unit) {
        viewModelScope.launch {
            library.delete(bookId)
            onRemoved()
        }
    }

    override fun onCleared() {
        session?.close()
        session = null
    }

    @AssistedFactory
    interface Factory {
        fun create(bookId: String): ReaderViewModel
    }

    private companion object {
        const val FINISH_THRESHOLD = 0.99
    }
}
