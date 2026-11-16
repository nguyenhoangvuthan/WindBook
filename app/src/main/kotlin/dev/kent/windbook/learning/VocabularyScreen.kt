package dev.kent.windbook.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.notes.NotesViewModel
import dev.kent.windbook.core.data.learning.LearningAnswer

@Composable
fun SavedLearningAnswers(notes:NotesViewModel,annotationId:String) {
    val records by remember(notes,annotationId) {notes.learning.answers(annotationId)}.collectAsStateWithLifecycle(emptyList())
    if(records.isEmpty()) return
    var expanded by rememberSaveable(annotationId) {mutableStateOf(false)}
    TextButton(onClick={expanded=!expanded}) {Text(stringResource(R.string.ai_answers))}
    if(expanded) records.forEach {record ->
        val answer=remember(record.payload) {runCatching {LearningAnswer.parse(record.payload)}.getOrNull()}
        if(answer==null) Text(stringResource(R.string.ai_error_response)) else {
            Text(stringResource(R.string.ai_generated))
            LearningAnswerSections(answer,onWord={notes.saveWord(record.id,it)})
        }
    }
}

@Composable
fun VocabularyDialog(notes:NotesViewModel,onClose:()->Unit,onOpen:(String,String)->Unit) {
    val words by notes.vocabulary.collectAsStateWithLifecycle()
    var query by rememberSaveable {mutableStateOf("")}
    AlertDialog(onDismissRequest=onClose,title={Text(stringResource(R.string.ai_vocabulary))},text={Column {
        OutlinedTextField(value=query,onValueChange={query=it},label={Text(stringResource(R.string.notes_search))},singleLine=true)
        val visible=words.filter {query.isBlank() || it.word.term.contains(query,true) || it.word.meaning.contains(query,true)}
        if(visible.isEmpty()) Text(stringResource(R.string.ai_vocabulary_empty))
        LazyColumn(Modifier.heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(visible,key={it.word.id}) {row ->Column {
                Text(row.word.term,style=MaterialTheme.typography.titleMedium)
                Text(row.word.meaning)
                Row {
                    TextButton(onClick={onOpen(row.bookId,row.annotationId)}) {Text(stringResource(R.string.notes_open))}
                    TextButton(onClick={notes.deleteWord(row.word.id)}) {Text(stringResource(R.string.notes_delete))}
                }
            }}
        }
    }},confirmButton={TextButton(onClick=onClose) {Text(stringResource(R.string.close))}})
}
