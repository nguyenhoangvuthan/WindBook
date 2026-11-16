package dev.kent.windbook.learning

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.data.learning.GeminiKeyStore
import kotlinx.coroutines.*

@Composable
fun AiSettingsDialog(onClose:()->Unit) {
    val context=LocalContext.current
    val store=remember(context) {GeminiKeyStore(context)}
    var key by remember {mutableStateOf("")}
    var saved by remember {mutableStateOf(false)}
    var failed by remember {mutableStateOf(false)}
    var working by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(store) {saved=withContext(Dispatchers.IO) {store.read()!=null}}
    AlertDialog(onDismissRequest={if(!working) onClose()},title={Text(stringResource(R.string.ai_settings))},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.ai_key_hint))
        Text(stringResource(if(saved) R.string.ai_key_present else R.string.ai_key_missing))
        OutlinedTextField(value=key,onValueChange={key=it.take(512)},label={Text(stringResource(R.string.ai_key))},singleLine=true,
            visualTransformation=PasswordVisualTransformation(),enabled=!working,modifier=Modifier.fillMaxWidth())
        if(failed) Text(stringResource(R.string.ai_error_key),color=MaterialTheme.colorScheme.error)
        if(saved) TextButton(enabled=!working,onClick={working=true;scope.launch {
            try {withContext(Dispatchers.IO) {store.remove()};saved=false;key="";failed=false}
            catch(e:CancellationException){throw e} catch(_:Exception){failed=true} finally {working=false}
        }}) {Text(stringResource(R.string.ai_remove_key))}
    }},confirmButton={TextButton(enabled=!working && key.isNotBlank(),onClick={working=true;scope.launch {
        try {withContext(Dispatchers.IO) {store.save(key)};key="";saved=true;failed=false;onClose()}
        catch(e:CancellationException){throw e} catch(_:Exception){failed=true} finally {working=false}
    }}) {Text(stringResource(R.string.notes_save))}},dismissButton={TextButton(onClick=onClose,enabled=!working) {Text(stringResource(R.string.close))}})
}
