package dev.kent.windbook.learning

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.data.learning.*

@Composable
fun LearningCard(viewModel:LearningViewModel,onClose:()->Unit={}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val target=state.target ?: return
    var question by rememberSaveable(target) {mutableStateOf("")}
    var settings by remember {mutableStateOf(false)}
    var prepareConfirm by remember {mutableStateOf(false)}
    val close={viewModel.dismiss();onClose()}
    AlertDialog(onDismissRequest=close,title={Text(stringResource(R.string.ai_translate))},text={
        Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(target.location,style=MaterialTheme.typography.labelSmall)
            Text(target.quote,style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.ai_disclosure),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(if(state.complete) R.string.ai_prepared else R.string.ai_partial,state.prepared))
            TextButton(onClick={settings=true},enabled=!state.working) {Text(stringResource(R.string.ai_settings))}
            if(!state.complete) TextButton(onClick={prepareConfirm=true},enabled=!state.working) {Text(stringResource(R.string.ai_prepare))}
            state.failure?.let {Text(stringResource(it.messageRes()),color=MaterialTheme.colorScheme.error)}
            if(state.working) {LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=viewModel::cancel) {Text(stringResource(R.string.notes_cancel))}}
            state.answer?.let {answer ->
                Text(stringResource(R.string.ai_generated),style=MaterialTheme.typography.labelMedium)
                LearningAnswerSections(answer,onWord={viewModel.save(it)},enabled=!state.working,addedWords=state.addedWords)
                TextButton(onClick={viewModel.save()},enabled=!state.working && state.savedResultId!=state.resultId) {
                    Text(stringResource(if(state.savedResultId!=state.resultId) R.string.ai_save else R.string.ai_saved))
                }
            }
            OutlinedTextField(value=question,onValueChange={question=it.take(2048)},label={Text(stringResource(R.string.ai_question))},
                enabled=!state.working,maxLines=4,modifier=Modifier.fillMaxWidth())
            TextButton(onClick={viewModel.generate(question)},enabled=!state.working) {Text(stringResource(R.string.ai_generate))}
        }
    },confirmButton={TextButton(onClick=close) {Text(stringResource(R.string.close))}})
    if(settings) AiSettingsDialog {settings=false}
    if(prepareConfirm) AlertDialog(onDismissRequest={prepareConfirm=false},title={Text(stringResource(R.string.ai_prepare))},
        text={Text(stringResource(R.string.ai_prepare_confirm))},confirmButton={TextButton(onClick={prepareConfirm=false;viewModel.prepare()}) {Text(stringResource(R.string.ai_send))}},
        dismissButton={TextButton(onClick={prepareConfirm=false}) {Text(stringResource(R.string.notes_cancel))}})
}

@Composable
fun LearningAnswerSections(answer:LearningAnswer,onWord:(LearningWord)->Unit,enabled:Boolean=true,addedWords:Set<String> = emptySet()) {
    listOf(R.string.ai_translation to answer.translation,R.string.ai_grammar to answer.grammar,
        R.string.ai_idioms to answer.idioms,R.string.ai_context to answer.context).forEach { (title,text) ->
        var expanded by rememberSaveable(title) {mutableStateOf(title==R.string.ai_translation)}
        TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {Text(stringResource(title))}
        if(expanded) Text(text)
    }
    Text(stringResource(R.string.ai_vocabulary),style=MaterialTheme.typography.titleSmall)
    answer.words.forEach {word ->
        Text("${word.term}: ${word.meaning}")
        TextButton(onClick={onWord(word)},enabled=enabled && word.term !in addedWords) {Text(stringResource(if(word.term in addedWords) R.string.ai_saved else R.string.ai_add_word))}
    }
}
fun AiFailure.messageRes()=when(this) {
    AiFailure.KEY->R.string.ai_error_key
    AiFailure.QUOTA->R.string.ai_error_quota
    AiFailure.NETWORK->R.string.ai_error_network
    AiFailure.RESPONSE->R.string.ai_error_response
    AiFailure.LIMIT->R.string.ai_error_limit
}
