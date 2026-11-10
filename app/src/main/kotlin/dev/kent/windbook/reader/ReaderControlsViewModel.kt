package dev.kent.windbook.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.core.data.*
import dev.kent.windbook.core.data.db.BookmarkEntity
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.metadata.PdfContentsExtractor
import dev.kent.windbook.reader.source.SourceAccess
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class NavigationRequest(val id: Long,val locatorJson: String)
internal data class ContentsUi(val loading: Boolean=false,val entries: List<ContentsEntry> = emptyList(),val failed: Boolean=false,val unavailable: Boolean=false)
internal data class SearchUi(val query: String="",val loading: Boolean=false,val hits: List<ReaderSearchHit> = emptyList(),val more: Boolean=false,val failed: Boolean=false,val limited: Boolean=false)

@HiltViewModel(assistedFactory=ReaderControlsViewModel.Factory::class)
class ReaderControlsViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val library: LibraryRepository,
    private val bookmarksRepository: BookmarkRepository,
    private val contentsRepository: PdfContentsRepository,
    private val contentsExtractor: PdfContentsExtractor,
    private val sourceAccess: SourceAccess,
    private val bookPreferences: BookTypographyRepository,
    private val defaults: ReaderPreferencesRepository,
    private val navigation: ReaderNavigationAccess = DefaultReaderNavigationAccess,
    private val cleanupScope: CoroutineScope,
): ViewModel() {
    private var session: ReaderSession?=null
    private var source: BookSource?=null
    private var sha: String?=null
    private var binding: Job?=null
    private var bookmarkJob: Job?=null
    private var contentsJob: Job?=null
    private var searchJob: Job?=null
    private class SearchLease(val reader: ReaderSearchCursor) {
        private val closed=java.util.concurrent.atomic.AtomicBoolean()
        fun close() {if(closed.compareAndSet(false,true)) reader.close()}
    }
    private var cursor: SearchLease?=null
    private var seekJob: Job?=null
    private var seekGeneration=0L
    private var sequence=0L
    private var searchGeneration=0L
    private val history=ReaderJumpHistory()
    private var origin: String?=null
    private var returning=false
    private var locator: String?=null
    private var progression=0.0
    private var chapter: String?=null
    internal val contents=MutableStateFlow(ContentsUi())
    internal val bookmarks=MutableStateFlow<List<BookmarkEntity>>(emptyList())
    internal val request=MutableStateFlow<NavigationRequest?>(null)
    internal val search=MutableStateFlow(SearchUi())
    internal val canReturn=MutableStateFlow(false)
    internal val message=MutableStateFlow<Int?>(null)
    internal val canBookmark=MutableStateFlow(false)
    val bookTypography=bookPreferences.observe(bookId).stateIn(viewModelScope,SharingStarted.Eagerly,ReaderTypography())
    val globalTypography=defaults.typography.stateIn(viewModelScope,SharingStarted.Eagerly,ReaderTypography())
    val typography=combine(bookTypography,globalTypography) {book,global->book.resolveOver(global)}
        .stateIn(viewModelScope,SharingStarted.Eagerly,ReaderTypography())

    internal fun bind(value: ReaderSession, bookSource: BookSource?,initial: String?,fraction: Double) {
        if(session===value) return
        binding?.cancel();bookmarkJob?.cancel();contentsJob?.cancel();cancelSearch();invalidateSeek()
        session=value;source=bookSource;sha=null;locator=initial;progression=fraction
        history.clear();canReturn.value=false;request.value=null;contents.value=ContentsUi();bookmarks.value=emptyList();canBookmark.value=false
        binding=viewModelScope.launch {
            try {
                val fingerprint=value.fingerprint() ?: return@launch
                val book=library.book(bookId) ?: return@launch
                if(session!==value || book.sha256!=fingerprint) return@launch
                sha=fingerprint;canBookmark.value=true
                bookmarkJob=viewModelScope.launch {
                    bookmarksRepository.observe(bookId,fingerprint,value.textEncoding?.name).collect {if(session===value) bookmarks.value=it}
                }
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {canBookmark.value=false}
        }
    }
    internal fun track(json: String,fraction: Double,title: String?) {locator=json;progression=fraction;chapter=title}
    internal fun loadContents() {
        if(contentsJob?.isActive==true) return
        val expected=session ?: return
        contents.value=ContentsUi(loading=true)
        contentsJob=viewModelScope.launch {
            try {
                binding?.join()
                if(session!==expected) return@launch
                if(expected !is PdfSession) {
                    val entries=ReaderNavigationController(expected).contents()
                    if(session===expected) contents.value=ContentsUi(entries=entries)
                    return@launch
                }
                val fingerprint=sha ?: throw ReaderException("No verified source")
                var result=contentsRepository.find(bookId,fingerprint)
                if(result==null) {
                    val currentSource=source ?: throw ReaderException("Source unavailable")
                    val before=sourceAccess.metadata(currentSource)
                    sourceAccess.acquire(currentSource).use {lease ->
                        if(lease.fingerprint()!=fingerprint) throw ReaderException("Source revision changed")
                        result=contentsExtractor.read(lease.file,expected.pageCount)
                        currentCoroutineContext().ensureActive()
                        if(session!==expected || before.changed(sourceAccess.metadata(currentSource))) throw ReaderException("Source changed")
                        if(!contentsRepository.save(bookId,fingerprint,requireNotNull(result))) throw ReaderException("Revision changed")
                    }
                }
                if(session===expected) {val prepared=requireNotNull(result);contents.value=ContentsUi(entries=prepared.entries,unavailable=prepared.status==ContentsStatus.UNAVAILABLE)}
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {if(session===expected) contents.value=ContentsUi(failed=true)}
        }
    }
    internal fun jumpTo(entry: ContentsEntry) {
        val current=session ?: return
        val target=if(current is PdfSession) entry.page?.takeIf {it in 1..current.pageCount}?.let {current.locatorJsonFor(it-1)} else entry.locatorJson
        if(target!=null) navigate(target) else message.value=dev.kent.windbook.R.string.reader_destination_unavailable
    }
    internal fun invalidateSeek() {seekGeneration++;seekJob?.cancel();seekJob=null}
    internal fun seek(fraction: Double) {
        val expected=session ?: return
        val token=++seekGeneration
        seekJob?.cancel()
        seekJob=viewModelScope.launch {
            try {
                val target=if(expected is PdfSession) seekPage(fraction,expected.pageCount)?.let(expected::locatorJsonFor)
                    else navigation.position(expected,fraction)
                if(session===expected && token==seekGeneration && target!=null) navigate(target,fromSeek=true)
            } catch(e: CancellationException) {throw e}
            catch(_: Exception) {if(session===expected) message.value=dev.kent.windbook.R.string.reader_destination_unavailable}
        }
    }
    internal fun page(page: Int) {
        val current=session as? PdfSession ?: return
        if(page in 1..current.pageCount) navigate(current.locatorJsonFor(page-1))
    }
    internal fun navigate(target: String,back: Boolean=false,fromSeek: Boolean=false) {
        if(!fromSeek) invalidateSeek()
        if(request.value!=null || locator==target) return
        val pdf=session as? PdfSession
        if(pdf!=null && pdfNavigationPage(target,pdf.pageCount)==null) {message.value=dev.kent.windbook.R.string.reader_destination_unavailable;return}
        if(pdf!=null && locator!=null && pdf.pageOf(target)==pdf.pageOf(locator)) return
        origin=locator;returning=back
        request.value=NavigationRequest(++sequence,target)
    }
    internal fun completed(id: Long,success: Boolean) = completed(id,if(success) NavigationResult.COMPLETED else NavigationResult.FAILED)
    internal fun completed(id: Long,result: NavigationResult) {
        val pending=request.value?.takeIf {it.id==id} ?: return
        if(result==NavigationResult.COMPLETED) {
            if(returning) history.pop() else history.record(origin,pending.locatorJson)
            canReturn.value=history.size>0
        } else if(result==NavigationResult.FAILED) message.value=dev.kent.windbook.R.string.reader_destination_unavailable
        else if(returning) {history.pop();canReturn.value=history.size>0}
        request.value=null;origin=null
    }
    internal fun backToLocation() {history.peek()?.let {navigate(it,true)}}
    internal fun bookmark() {
        val expected=session ?: return
        val json=locator ?: return
        val fingerprint=sha ?: return
        val label=chapter?.takeIf {it.isNotBlank()} ?: if(expected is PdfSession) "${expected.pageOf(json)+1}" else "${(progression*100).toInt()}%"
        val fraction=progression
        viewModelScope.launch {
            val id=bookmarksRepository.save(bookId,fingerprint,json,fraction,label,expected.textEncoding?.name)
            if(session===expected) message.value=if(id!=null) dev.kent.windbook.R.string.reader_bookmark_saved else dev.kent.windbook.R.string.reader_destination_unavailable
        }
    }
    internal fun renameBookmark(id: String,label: String) {if(label.isNotBlank()) viewModelScope.launch {bookmarksRepository.rename(id,label.take(512))}}
    internal fun deleteBookmark(id: String) {viewModelScope.launch {bookmarksRepository.remove(id)}}
    fun setTypography(change: (ReaderTypography)->ReaderTypography,global: Boolean=false) {viewModelScope.launch {if(global) defaults.update(change) else bookPreferences.update(bookId,change)}}
    internal fun runSearch(query: String) {
        val expected=session ?: return
        val previous=searchJob
        val token=++searchGeneration
        val clean=query.take(256)
        search.value=SearchUi(clean,loading=clean.isNotBlank())
        searchJob=viewModelScope.launch {
            previous?.cancelAndJoin();cursor?.close();cursor=null
            if(clean.isBlank() || expected is PdfSession) return@launch
            delay(250)
            var owned: SearchLease?=null
            var retained=false
            try {
                owned=SearchLease(navigation.search(expected,clean) ?: throw ReaderException("Search unavailable"))
                currentCoroutineContext().ensureActive()
                if(session!==expected || token!=searchGeneration) return@launch
                cursor=owned
                val hits=withContext(Dispatchers.Default) {owned.reader.next()}
                if(session===expected && searchGeneration==token) {
                    search.value=SearchUi(clean,hits=hits,more=!owned.reader.exhausted)
                    retained=true
                }
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(searchGeneration==token) search.value=SearchUi(clean,failed=true,limited=e is ReaderSearchLimitException)}
            finally {if(!retained) {if(cursor===owned) cursor=null;owned?.close()}}
        }
    }
    internal fun moreSearch() {
        if(searchJob?.isActive==true) return
        val current=cursor ?: return
        val token=searchGeneration
        search.value=search.value.copy(loading=true)
        searchJob=viewModelScope.launch {
            try {
                val hits=withContext(Dispatchers.Default) {current.reader.next()}
                if(searchGeneration==token) search.value=search.value.copy(loading=false,hits=hits,more=!current.reader.exhausted)
            } catch(e: CancellationException) {throw e}
            catch(e: Exception) {if(searchGeneration==token) search.value=search.value.copy(loading=false,failed=true,limited=e is ReaderSearchLimitException)}
        }
    }
    internal fun cancelSearch() {
        searchGeneration++
        val previous=searchJob;previous?.cancel()
        val closing=cursor;cursor=null
        cleanupScope.launch {previous?.join();closing?.close()}
        search.value=SearchUi()
    }
    override fun onCleared() {cancelSearch();session=null}
    @AssistedFactory interface Factory {fun create(bookId: String): ReaderControlsViewModel}
}
