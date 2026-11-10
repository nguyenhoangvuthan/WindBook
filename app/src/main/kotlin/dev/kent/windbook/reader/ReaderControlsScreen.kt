package dev.kent.windbook.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import dev.kent.windbook.core.data.db.BookmarkEntity
import dev.kent.windbook.reader.pdf.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderReadyScreen(bookId: String,current: ReaderUiState.Ready,reader: ReaderViewModel,onBack: ()->Unit,
    annotationId: String?,revealRequestId: String?,onEncoding: ()->Unit) {
    val controls=hiltViewModel<ReaderControlsViewModel,ReaderControlsViewModel.Factory>(key="controls:$bookId",creationCallback={it.create(bookId)})
    val overlay by reader.overlayVisible.collectAsStateWithLifecycle()
    val progression by reader.progression.collectAsStateWithLifecycle()
    val chapter by reader.chapterTitle.collectAsStateWithLifecycle()
    val finished by reader.canMarkFinished.collectAsStateWithLifecycle()
    val display by reader.pdfDisplay.collectAsStateWithLifecycle()
    val request by controls.request.collectAsStateWithLifecycle()
    val contents by controls.contents.collectAsStateWithLifecycle()
    val bookmarks by controls.bookmarks.collectAsStateWithLifecycle()
    val canBookmark by controls.canBookmark.collectAsStateWithLifecycle()
    val canReturn by controls.canReturn.collectAsStateWithLifecycle()
    val message by controls.message.collectAsStateWithLifecycle()
    val bookTypography by controls.bookTypography.collectAsStateWithLifecycle()
    val globalTypography by controls.globalTypography.collectAsStateWithLifecycle()
    val search by controls.search.collectAsStateWithLifecycle()
    val drawer=rememberDrawerState(DrawerValue.Closed)
    val scope=rememberCoroutineScope()
    var appearance by remember(current.session) {mutableStateOf(false)}
    var showBookmarks by remember(current.session) {mutableStateOf(false)}
    var showSearch by remember(current.session) {mutableStateOf(false)}
    var pageEntry by remember(current.session) {mutableStateOf(false)}
    val pdf=current.session as? PdfSession
    LaunchedEffect(current.session) {controls.bind(current.session,reader.readingSource(),reader.latestLocatorJson ?: current.initialLocatorJson,progression)}
    LaunchedEffect(current.session,display?.layout,bookTypography,globalTypography) {controls.invalidateSeek()}
    DisposableEffect(reader,controls,current.session) {
        reader.locationObserver=controls::track
        onDispose {reader.locationObserver=null;controls.cancelSearch()}
    }
    BackHandler(drawer.isOpen) {scope.launch {drawer.close()}}
    ModalNavigationDrawer(drawerState=drawer,gesturesEnabled=false,drawerContent={
        ModalDrawerSheet(modifier=Modifier.widthIn(max=360.dp).fillMaxHeight()) {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.reader_contents),style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                TextButton(onClick={scope.launch {drawer.close()}}) {Text(stringResource(R.string.close))}
            }
            when {
                contents.loading -> Text(stringResource(R.string.reader_contents_loading),Modifier.padding(20.dp))
                contents.failed -> Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.reader_contents_failed))
                    TextButton(onClick=controls::loadContents) {Text(stringResource(R.string.reader_retry))}
                }
                contents.unavailable -> Text(stringResource(R.string.reader_contents_unavailable),Modifier.padding(20.dp))
                contents.entries.isEmpty() -> Text(stringResource(R.string.reader_contents_empty),Modifier.padding(20.dp))
                else -> ReaderContentsList(contents.entries,chapter) {entry ->controls.jumpTo(entry);scope.launch {drawer.close()}}
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) {
            if(pdf!=null) display?.let {prefs ->
                val initial=remember(pdf) {reader.initialPdfPage()}
                PdfNotesReader(bookId,pdf,reader,prefs,initial,annotationId,revealRequestId,
                    pageJump=request?.let {PdfPageJump(it.id,pdf.pageOf(it.locatorJson))},onPageJumpApplied=controls::completed)
            } else ReaderSurface(current,reader,controls)
            val page=if(pdf!=null) pdf.pageOf(reader.latestLocatorJson)+1 else null
            val sliderFraction=if(pdf!=null && pdf.pageCount>1) (page!!-1).toDouble()/(pdf.pageCount-1) else if(pdf!=null) 0.0 else progression
            ReaderOverlay(visible=overlay,title=current.title,chapterTitle=chapter,progression=progression,
                canMarkFinished=finished,onMarkFinished=reader::markFinished,onBack=onBack,
                pdfDisplay=if(pdf!=null) display ?: EffectiveDisplay(PdfLayout.PAGED,false) else null,
                onPdfLayoutChange=reader::setPdfLayout,onCropMarginsChange=reader::setCropMargins,onFitPdfPage=reader::fitPdfPage,
                onContents={controls.loadContents();scope.launch {drawer.open()}},onBookmark=controls::bookmark,onBookmarks={showBookmarks=true},
                onSearch={showSearch=true},onAppearance={appearance=true},onEncoding=if(current.session.textEncoding!=null) onEncoding else null,
                canBookmark=canBookmark,onSeek=controls::seek,canSeek=request==null,pdfPage=page,pdfPageCount=pdf?.pageCount,onPageEntry={pageEntry=true},
                canReturn=canReturn,onReturn=controls::backToLocation,seekFraction=sliderFraction)
            message?.let {res -> Snackbar(modifier=Modifier.align(Alignment.BottomCenter),action={TextButton(onClick={controls.message.value=null}) {Text(stringResource(R.string.close))}}) {Text(stringResource(res))}}
        }
    }
    if(pageEntry && pdf!=null) ReaderPageJumpDialog(pdf.pageOf(reader.latestLocatorJson)+1,pdf.pageCount,{pageEntry=false}) {pageEntry=false;controls.page(it)}
    if(appearance) ReaderTypographySheet(bookTypography,globalTypography,{appearance=false},{value,global->controls.setTypography(value,global)},ReaderNavigationController(current.session).reflowable)
    if(showBookmarks) ReaderBookmarksDialog(bookmarks,{showBookmarks=false},{showBookmarks=false;controls.navigate(it.locatorJson)},controls::renameBookmark,controls::deleteBookmark)
    if(showSearch) ReaderSearchSheet(search,{showSearch=false;controls.cancelSearch()},controls::runSearch,controls::moreSearch) {hit ->showSearch=false;controls.cancelSearch();controls.navigate(hit.locatorJson)}
}

@Composable
internal fun ReaderContentsList(entries: List<ContentsEntry>,currentTitle: String?,onSelect: (ContentsEntry)->Unit) {
    val rows=remember(entries) {
        buildList<Pair<ContentsEntry,Int>> {
            fun addNodes(nodes: List<ContentsEntry>,depth: Int) {nodes.forEach {add(it to depth);addNodes(it.children,depth+1)}}
            addNodes(entries,0)
        }
    }
    LazyColumn {
        items(rows,key={it.first.id}) {(entry,depth)->
            val enabled=entry.page!=null || entry.locatorJson!=null
            Row(Modifier.fillMaxWidth().background(if(currentTitle!=null && entry.title==currentTitle) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                .clickable(enabled=enabled,role=Role.Button) {onSelect(entry)}.heightIn(min=48.dp).padding(start=20.dp+(depth.coerceAtMost(6)*16).dp,end=16.dp,top=12.dp,bottom=12.dp)) {
                Text(entry.title.ifBlank {stringResource(R.string.reader_section,rows.indexOfFirst {it.first.id==entry.id}+1)},modifier=Modifier.weight(1f),color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                entry.page?.let {Text(it.toString(),Modifier.padding(start=8.dp))}
            }
        }
    }
}

@Composable
internal fun ReaderPageJumpDialog(current: Int,count: Int,onDismiss: ()->Unit,onGo: (Int)->Unit) {
    var text by remember {mutableStateOf(current.toString())}
    val page=text.toIntOrNull()?.takeIf {it in 1..count}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.reader_page_jump))},text={
        OutlinedTextField(value=text,onValueChange={text=it.take(12)},singleLine=true,label={Text(stringResource(R.string.reader_page_hint,count))},
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),isError=page==null)
    },confirmButton={TextButton(enabled=page!=null,onClick={page?.let(onGo)}) {Text(stringResource(R.string.reader_go))}},
        dismissButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.close))}})
}

@Composable
private fun ReaderBookmarksDialog(records: List<BookmarkEntity>,onDismiss: ()->Unit,onSelect: (BookmarkEntity)->Unit,
    onRename: (String,String)->Unit,onDelete: (String)->Unit) {
    var editing by remember {mutableStateOf<BookmarkEntity?>(null)}
    var name by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text(stringResource(R.string.reader_bookmarks))},text={
        if(records.isEmpty()) Text(stringResource(R.string.reader_bookmarks_empty)) else LazyColumn(Modifier.heightIn(max=480.dp)) {
            items(records,key={it.id}) {bookmark ->
                Column {
                    TextButton(onClick={onSelect(bookmark)}) {Text(bookmark.label)}
                    Row {
                        TextButton(onClick={editing=bookmark;name=bookmark.label}) {Text(stringResource(R.string.reader_rename))}
                        TextButton(onClick={onDelete(bookmark.id)}) {Text(stringResource(R.string.reader_delete_bookmark))}
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss) {Text(stringResource(R.string.close))}})
    editing?.let {bookmark ->AlertDialog(onDismissRequest={editing=null},title={Text(stringResource(R.string.reader_rename))},text={
        OutlinedTextField(value=name,onValueChange={name=it.take(512)},label={Text(stringResource(R.string.reader_bookmark_name))})
    },confirmButton={TextButton(enabled=name.isNotBlank(),onClick={onRename(bookmark.id,name);editing=null}) {Text(stringResource(R.string.reader_save))}},dismissButton={TextButton(onClick={editing=null}) {Text(stringResource(R.string.close))}})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderSearchSheet(state: SearchUi,onDismiss: ()->Unit,onQuery: (String)->Unit,onMore: ()->Unit,onSelect: (ReaderSearchHit)->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp).imePadding()) {
            Text(stringResource(R.string.reader_search),style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(value=state.query,onValueChange=onQuery,label={Text(stringResource(R.string.reader_search_hint))},singleLine=true,modifier=Modifier.fillMaxWidth())
            when {
                state.loading -> Text(stringResource(R.string.reader_search_loading),Modifier.padding(vertical=16.dp))
                state.failed -> Text(stringResource(if(state.limited) R.string.reader_search_limit else R.string.reader_search_failed),Modifier.padding(vertical=16.dp))
                state.query.isNotBlank() && state.hits.isEmpty() -> Text(stringResource(R.string.reader_search_empty),Modifier.padding(vertical=16.dp))
            }
            LazyColumn(Modifier.heightIn(max=420.dp)) {items(state.hits) {hit -> TextButton(onClick={onSelect(hit)}) {Text(hit.snippet)}}}
            if(state.more) TextButton(enabled=!state.loading,onClick=onMore) {Text(stringResource(R.string.reader_search_next))}
        }
    }
}
