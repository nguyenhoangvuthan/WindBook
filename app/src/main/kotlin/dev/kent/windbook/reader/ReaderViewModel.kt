package dev.kent.windbook.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.Verification
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceMetadata
import dev.kent.windbook.reader.source.SourceLease
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    data object Reconnect : ReaderUiState
    data class Changed(val newBookId: String) : ReaderUiState
    data class Redirect(val bookId: String) : ReaderUiState
    data class HiddenMatch(val bookId: String) : ReaderUiState
}

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val library: LibraryRepository,
    private val opener: BookOpener,
    private val displayPrefs: DisplayPreferencesRepository,
    private val sources: BookSourcesRepository,
    private val sourceAccess: SourceAccess,
    private val metadata: SourceMetadata,
    private val files: BookFileStore,
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

    private var openJob: Job? = null

    init {
        openJob = viewModelScope.launch { open() }
        viewModelScope.launch {
            locatorUpdates.collect { (json, progression) ->
                library.saveProgress(bookId, json, progression)
            }
        }
    }

    private suspend fun open(replacement: BookSource? = null) {
        _state.value = ReaderUiState.Loading
        val book = library.book(bookId) ?: run {
            _state.value = ReaderUiState.Error(ReaderError.NOT_FOUND)
            return
        }
        val candidates = if (replacement != null) listOf(replacement) else
            sources.sources(bookId).ifEmpty { book.filePath?.let { listOf(BookSource.Owned(it)) } ?: emptyList() }
        var pending: ReaderUiState? = null
        var acquiredAny = false
        for (source in candidates) {
            var lease: SourceLease? = null
            var opened: ReaderSession? = null
            try {
                val before = if (source is BookSource.External) sourceAccess.metadata(source) else null
                var enrichment: dev.kent.windbook.core.model.ExtractedMetadata? = null
                val result = if (source is BookSource.External) {
                    lease = sourceAccess.acquire(source)
                    val sha = lease.fingerprint()
                    if (before!!.changed(sourceAccess.metadata(source))) continue
                    val verification = if (replacement == null) sources.verify(bookId, source, sha)
                        else sources.reconnect(bookId, source, sha)
                    when (verification) {
                        is Verification.Changed -> { pending = ReaderUiState.Changed(verification.newBookId); continue }
                        is Verification.Redirect -> { pending = ReaderUiState.Redirect(verification.bookId); continue }
                        is Verification.HiddenMatch -> { pending = ReaderUiState.HiddenMatch(verification.bookId); continue }
                        is Verification.Ready -> Unit
                    }
                    acquiredAny = true
                    enrichment = try { metadata.read(lease, book.format).getOrNull() }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { null }
                    val transferred = lease
                    lease = null // BookOpener consumes the lease on success, failure and cancellation.
                    opener.open(transferred, book.format)
                } else {
                    val owned = source as BookSource.Owned
                    if (!File(owned.path).isFile) continue
                    acquiredAny = true
                    opener.open(File(owned.path), book.format)
                }
                opened = result.getOrThrow()
                currentCoroutineContext().ensureActive()
                // The engine reads the pinned snapshot. A changed/unavailable provider must not
                // receive saved anchors or mark this old revision opened.
                if (before != null && before.changed(sourceAccess.metadata(source))) continue
                val firstPages = if (opened is PdfSession) opened.firstPageSizes() else null
                currentCoroutineContext().ensureActive()
                enrichment?.let { info ->
                    try {
                        val cover = withContext(Dispatchers.IO) { info.coverWebp?.let { files.writeCover(bookId, it).path } }
                        sources.enrich(bookId, info, cover)
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Metadata is optional; a valid reader keeps its filename fallback. */ }
                }
                val savedLocator = library.savedLocator(bookId)
                library.markOpened(bookId)
                currentCoroutineContext().ensureActive()
                val title = library.book(bookId)?.title ?: book.title
                currentCoroutineContext().ensureActive()
                finished.value = book.status == ReadingStatus.FINISHED
                latestLocatorJson = savedLocator
                session = opened
                _state.value = ReaderUiState.Ready(opened, title, savedLocator)
                if (firstPages != null) viewModelScope.launch {
                    displayPrefs.observe(bookId).collect { prefs ->
                        currentPrefs = prefs
                        _pdfDisplay.value = prefs.resolve(firstPages)
                    }
                }
                opened = null // The ViewModel now owns the session.
                return
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A denied/missing source can coexist with a healthy owned or external source.
            } finally {
                opened?.close()
                lease?.close()
            }
        }
        _state.value = pending ?: when {
            candidates.any { it is BookSource.External } || book.filePath == null -> ReaderUiState.Reconnect
            acquiredAny -> ReaderUiState.Error(ReaderError.CANNOT_OPEN)
            else -> ReaderUiState.Error(ReaderError.FILE_MISSING)
        }
    }

    fun reconnect(source: BookSource) {
        openJob?.cancel()
        openJob = viewModelScope.launch { open(source) }
    }

    fun restoreHiddenMatch() {
        val hidden = _state.value as? ReaderUiState.HiddenMatch ?: return
        openJob = viewModelScope.launch {
            sources.restoreExternalBook(hidden.bookId)
            open()
        }
    }

    fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
        if (_state.value !is ReaderUiState.Ready) return
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
