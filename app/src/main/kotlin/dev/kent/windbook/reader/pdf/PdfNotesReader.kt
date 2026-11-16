package dev.kent.windbook.reader.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import dev.kent.windbook.notes.*
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.ReaderViewModel
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

@Composable
internal fun PdfNotesReader(bookId: String,session: PdfSession,reader: ReaderViewModel,display: EffectiveDisplay,
    initialPage: Int,annotationId: String?,revealRequestId: String?, pageJump: PdfPageJump? = null, onPageJumpApplied: (Long,Boolean)->Unit = {_,_->}) {
    val learning=hiltViewModel<dev.kent.windbook.learning.LearningViewModel>(key="learning:$bookId")
    val learningState by learning.state.collectAsStateWithLifecycle()
    DisposableEffect(session) {onDispose {learning.detach()}}
    val notes=hiltViewModel<NotesViewModel>(key="pdf-notes:$bookId")
    val fitRequestId by reader.pdfFitRequestId.collectAsStateWithLifecycle()
    val records by notes.annotations.collectAsStateWithLifecycle()
    val saving by notes.saving.collectAsStateWithLifecycle()
    val failed by notes.failed.collectAsStateWithLifecycle()
    val editorVisible by notes.editorVisible.collectAsStateWithLifecycle()
    val saved by notes.saved.collectAsStateWithLifecycle()
    var fingerprint by remember(session) { mutableStateOf<String?>(null) }
    var verified by remember(session) { mutableStateOf(false) }
    var selection by remember(session) { mutableStateOf<PdfTextSelection?>(null) }
    var active by remember(session) { mutableStateOf(emptyList<String>()) }
    var reveal by remember(session,revealRequestId) { mutableStateOf<PdfRevealRequest?>(null) }
    var message by remember { mutableStateOf<Int?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(session) {
        fingerprint=try { reader.annotationFingerprint(session) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { null }
        verified=true
    }
    LaunchedEffect(session,verified) {if(verified) learning.attach(session,fingerprint)}
    LaunchedEffect(session,verified,annotationId,revealRequestId) {
        if(!verified || annotationId==null) return@LaunchedEffect
        val note=notes.find(annotationId)
        val anchor=note?.anchor
        if(fingerprint==null || note?.bookId!=bookId || note.fingerprint!=fingerprint || anchor==null || anchor.page !in 0 until session.pageCount) {
            message=R.string.notes_unavailable
            return@LaunchedEffect
        }
        val size=try { session.pages.measure(anchor.page) }
        catch(e: CancellationException) { throw e }
        catch(_: Exception) { message=R.string.notes_unavailable;return@LaunchedEffect }
        if(abs(size.width-anchor.width)>.01f || abs(size.height-anchor.height)>.01f) { message=R.string.notes_unavailable;return@LaunchedEffect }
        reveal=PdfRevealRequest(revealRequestId ?: annotationId,anchor)
        active=listOf(annotationId)
    }
    LaunchedEffect(pageJump?.id) {if(pageJump!=null) {selection=null;active=emptyList();reveal=null}}
    LaunchedEffect(saved) { selection=null;active=emptyList() }
    val marks=records.filter {it.bookId==bookId && it.fingerprint==fingerprint && it.anchor!=null}
    val clear={selection=null;active=emptyList();message=null}
    fun prepare(showEditor: Boolean): Boolean {
        val existing=marks.singleOrNull {it.id in active}
        if(existing!=null) { notes.edit(existing,showEditor);return true }
        val anchor=selection?.anchor
        val sha=fingerprint
        if(anchor==null || sha==null) {message=R.string.notes_unavailable;return false}
        notes.begin(bookId,sha,anchor,showEditor)
        return true
    }
    Box(Modifier.fillMaxSize()) {
        PdfReaderView(session.pages,display.layout,display.cropMargins,initialPage,reader::onPdfPageChanged,reader::toggleOverlay,
            pageJump=pageJump,onPageJumpApplied=onPageJumpApplied,initialZoom=display.zoom,initialZoomX=display.zoomX,onZoomChanged=reader::setPdfZoom,reveal=reveal,fitRequestId=fitRequestId,
            annotations=PdfAnnotationUi(marks,selection,active,
                onSelect={selection=it;message=null;if(it!=null) active=emptyList()},onExisting={active=it;if(it.isNotEmpty()) selection=null},
                onAi={
                    val existing=marks.singleOrNull {it.id in active}
                    val anchor=existing?.anchor ?: selection?.anchor
                    val sha=fingerprint
                    if(anchor==null || sha==null) message=R.string.notes_unavailable
                    else learning.open(dev.kent.windbook.learning.LearningTarget(bookId,sha,pdf=anchor,annotationId=existing?.id),session)
                },onNote={prepare(true)},onColor={color -> if(prepare(false)) notes.highlight(color)},
                onDelete={deleting=it},onClear=clear,
                onNoText={unavailable -> message=if(unavailable) R.string.notes_text_unavailable else R.string.notes_no_text},
                saving=saving,editorVisible=editorVisible || learningState.target!=null,failed=failed))
        message?.let { Snackbar(modifier=Modifier.align(Alignment.BottomCenter),action={TextButton(onClick={message=null}) {Text(stringResource(R.string.notes_cancel))}}) {Text(stringResource(it))} }
    }
    dev.kent.windbook.learning.LearningCard(learning,onClose=clear)
    AnnotationEditor(notes)
    DeleteAnnotationDialog(deleting,onCancel={deleting=null},onConfirm={id->deleting=null;notes.delete(id)})
}
