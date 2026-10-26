package dev.kent.windbook.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import dev.kent.windbook.ui.components.*

@Composable
fun NotesRoute(onOpen: (String,String)->Unit) {
    val viewModel=hiltViewModel<NotesViewModel>()
    val records by viewModel.annotations.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    NotesScreen(records,onOpen={ onOpen(it.bookId,it.id) },onEdit={viewModel.edit(it)},onDelete={deleting=it.id})
    if(failed) Snackbar { Text(stringResource(R.string.notes_failed)) }
    AnnotationEditor(viewModel)
    DeleteAnnotationDialog(deleting,onCancel={deleting=null},onConfirm={id -> deleting=null;viewModel.delete(id)})
}

@Composable
fun NotesScreen(annotations: List<PdfTextAnnotation> = emptyList(), onOpen: (PdfTextAnnotation)->Unit = {},
    onEdit: (PdfTextAnnotation)->Unit = {},onDelete: (PdfTextAnnotation)->Unit = {}) {
    var query by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(TabScreenInsets)) {
        ScreenTitle(stringResource(R.string.tab_notes))
        OutlinedTextField(value=query,onValueChange={query=it},label={Text(stringResource(R.string.notes_search))},
            singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp))
        val visible=annotations.filter { query.isBlank() || it.quote.contains(query,true) || it.body.orEmpty().contains(query,true) }
        if(visible.isEmpty()) EmptyState(stringResource(R.string.notes_empty_title),stringResource(R.string.notes_empty_body))
        else LazyColumn(contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(visible,key={it.id}) { annotation ->
                Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(annotation.bookTitle,style=MaterialTheme.typography.titleMedium)
                        annotation.anchor?.let { Text(stringResource(R.string.notes_page,it.page+1),style=MaterialTheme.typography.labelMedium) }
                        Box(Modifier.fillMaxWidth().height(3.dp).background(annotation.color.toComposeColor()))
                        Text(annotation.quote,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.padding(vertical=8.dp))
                        Text(annotation.body ?: stringResource(R.string.notes_highlight),style=MaterialTheme.typography.bodyMedium)
                        if(annotation.anchor==null) Text(stringResource(R.string.notes_unavailable))
                        Row {
                            TextButton(onClick={onEdit(annotation)}) { Text(stringResource(R.string.notes_edit)) }
                            TextButton(onClick={onDelete(annotation)}) { Text(stringResource(R.string.notes_delete)) }
                            TextButton(onClick={onOpen(annotation)},enabled=annotation.anchor!=null) { Text(stringResource(R.string.notes_open)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnotationEditor(viewModel: NotesViewModel) {
    val draft by viewModel.editor.collectAsStateWithLifecycle()
    val visible by viewModel.editorVisible.collectAsStateWithLifecycle()
    val failed by viewModel.failed.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    var discard by rememberSaveable { mutableStateOf(false) }
    val value=draft ?: return
    if(!visible) return
    val cancel={ if(!saving) { if(value.body!=value.originalBody) discard=true else viewModel.cancel() } }
    AlertDialog(onDismissRequest=cancel,title={Text(stringResource(R.string.notes_note))},text={
        Column {
            Text(value.quote,maxLines=8,style=MaterialTheme.typography.bodyMedium)
            OutlinedTextField(value=value.body,onValueChange=viewModel::changeBody,label={Text(stringResource(R.string.notes_body))},
                enabled=!saving,minLines=3,maxLines=8,modifier=Modifier.fillMaxWidth())
            if(failed) Text(stringResource(R.string.notes_failed),color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick=viewModel::save,enabled=!saving && value.body.isNotBlank()) {Text(stringResource(R.string.notes_save))}},
        dismissButton={TextButton(onClick=cancel,enabled=!saving) {Text(stringResource(R.string.notes_cancel))}})
    if(discard) AlertDialog(onDismissRequest={discard=false},title={Text(stringResource(R.string.notes_discard))},
        text={Text(stringResource(R.string.notes_discard_confirm))},
        confirmButton={TextButton(onClick={discard=false;viewModel.cancel()}) {Text(stringResource(R.string.notes_discard))}},
        dismissButton={TextButton(onClick={discard=false}) {Text(stringResource(R.string.notes_cancel))}})
}

@Composable
fun DeleteAnnotationDialog(id: String?,onCancel: ()->Unit,onConfirm: (String)->Unit) {
    if(id==null) return
    AlertDialog(onDismissRequest=onCancel,text={Text(stringResource(R.string.notes_delete_confirm))},
        confirmButton={TextButton(onClick={onConfirm(id)}) {Text(stringResource(R.string.notes_delete))}},
        dismissButton={TextButton(onClick=onCancel) {Text(stringResource(R.string.notes_cancel))}})
}

fun AnnotationColor.toComposeColor(): Color = when(this) {
    AnnotationColor.GOLD -> Color(0xFFE7B63D)
    AnnotationColor.GREEN -> Color(0xFF78B77B)
    AnnotationColor.BLUE -> Color(0xFF70A8D9)
    AnnotationColor.PINK -> Color(0xFFE891B1)
    AnnotationColor.PURPLE -> Color(0xFFB28BD4)
}
fun AnnotationColor.labelRes(): Int = when(this) {
    AnnotationColor.GOLD -> R.string.notes_gold
    AnnotationColor.GREEN -> R.string.notes_green
    AnnotationColor.BLUE -> R.string.notes_blue
    AnnotationColor.PINK -> R.string.notes_pink
    AnnotationColor.PURPLE -> R.string.notes_purple
}
