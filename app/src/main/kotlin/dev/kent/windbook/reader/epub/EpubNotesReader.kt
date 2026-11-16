package dev.kent.windbook.reader.epub

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import dev.kent.windbook.notes.*
import dev.kent.windbook.reader.*
import kotlinx.coroutines.CancellationException

@Composable
internal fun EpubNotesReader(bookId: String, session: ReaderSession, reader: ReaderViewModel,
    host: ReaderHostFragment?, selection: EpubTextAnchor?, selectedAi: Boolean, activeId: String?,
    annotationId: String?, revealRequestId: String?, clear: ()->Unit) {
    val learning=hiltViewModel<dev.kent.windbook.learning.LearningViewModel>(key="learning:$bookId")
    var showLearning by remember(session,selection) {mutableStateOf(selectedAi)}
    val notes=hiltViewModel<NotesViewModel>(key="epub-notes:$bookId")
    val records by notes.annotations.collectAsStateWithLifecycle()
    val saved by notes.saved.collectAsStateWithLifecycle()
    val saving by notes.saving.collectAsStateWithLifecycle()
    val editorVisible by notes.editorVisible.collectAsStateWithLifecycle()
    var fingerprint by remember(session) {mutableStateOf<String?>(null)}
    var verified by remember(session) {mutableStateOf(false)}
    var unavailable by remember(session) {mutableStateOf(false)}
    LaunchedEffect(session) {
        fingerprint=try {reader.annotationFingerprint(session)} catch(e: CancellationException){throw e} catch(_: Exception){null}
        verified=true
    }
    DisposableEffect(session) {onDispose {learning.detach()}}
    LaunchedEffect(session,verified) {if(verified) learning.attach(session,fingerprint)}
    LaunchedEffect(showLearning,selection,fingerprint) {
        if(showLearning && selection!=null && fingerprint!=null) learning.open(
            dev.kent.windbook.learning.LearningTarget(bookId,fingerprint!!,epub=selection),session)
    }
    LaunchedEffect(host,records,fingerprint) {
        host?.applyAnnotations(records.filter {it.bookId==bookId && it.fingerprint==fingerprint && it.epubAnchor!=null})
    }
    LaunchedEffect(host,verified,annotationId,revealRequestId) {
        if(!verified || host==null || annotationId==null) return@LaunchedEffect
        val note=notes.find(annotationId)
        val anchor=note?.epubAnchor
        if(note?.bookId!=bookId || fingerprint==null || note.fingerprint!=fingerprint || anchor==null) unavailable=true
        else if(host.navigate(anchor.locatorJson)==NavigationResult.FAILED) unavailable=true
    }
    LaunchedEffect(activeId) {
        if(activeId!=null) records.find {it.id==activeId && it.bookId==bookId && it.fingerprint==fingerprint}?.let {notes.edit(it)}
    }
    LaunchedEffect(saved) { if(saved>0) {host?.clearSelection();clear()} }
    if(selection!=null && !editorVisible && !showLearning) AlertDialog(onDismissRequest={if(!saving) clear()},
        title={Text(stringResource(R.string.notes_highlight))},text={Column {
            Text(selection.quote,maxLines=8)
            TextButton(onClick={showLearning=true},enabled=fingerprint!=null) {Text(stringResource(R.string.ai_translate))}
            AnnotationColor.entries.forEach {color -> TextButton(enabled=!saving,onClick={
                val sha=fingerprint
                if(sha==null) unavailable=true else {notes.beginEpub(bookId,sha,selection,false);notes.highlight(color)}
            }) {Text(stringResource(color.labelRes()))} }
        }},confirmButton={TextButton(enabled=!saving,onClick={
            val sha=fingerprint
            if(sha==null) unavailable=true else notes.beginEpub(bookId,sha,selection)
        }) {Text(stringResource(R.string.notes_note))}},dismissButton={TextButton(onClick=clear) {Text(stringResource(R.string.close))}})
    dev.kent.windbook.learning.LearningCard(learning,onClose={showLearning=false;clear()})
    AnnotationEditor(notes,onClosed=clear)
    if(unavailable) AlertDialog(onDismissRequest={unavailable=false},text={Text(stringResource(R.string.notes_unavailable))},
        confirmButton={TextButton(onClick={unavailable=false}) {Text(stringResource(R.string.close))}})
}
