package dev.kent.windbook.folders

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.ui.components.TabScreenInsets

@Composable
fun FolderRoute(folderId: String?, onOpenBook: (String) -> Unit, onBack: () -> Unit,
    viewModel: FolderViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(folderId) { folderId?.let { viewModel.selectFolder(it) } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.let { data ->
            data.data?.let { viewModel.onTreeGranted(it, data.flags) }
        }
    }
    val renderedFolderId = state.resolvedFolderId
    FolderScreen(state, FolderActions(
        onLocationEdited = viewModel::onLocationEdited,
        setOptions = { viewModel.setOptions(it, renderedFolderId) },
        scan = { viewModel.scan(renderedFolderId) }, stop = { viewModel.stop(renderedFolderId) }, setQuery = viewModel::setQuery,
        disconnect = { viewModel.disconnect(renderedFolderId) },
        pickFolder = { picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)) },
        selectFolder = { viewModel.selectFolder(it) },
        openBook = { viewModel.openBook(renderedFolderId, it, onOpenBook) },
        restore = { viewModel.restore(it, renderedFolderId) }, back = onBack, showHidden = viewModel::showHidden,
        setRecursive = { viewModel.setRecursive(it, renderedFolderId) }, toggleExtension = { viewModel.toggleExtension(it, renderedFolderId) },
        setExtensions = { viewModel.setExtensions(it, renderedFolderId) },
    ), canRead = viewModel::canRead)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderScreen(state: FolderUiState, actions: FolderActions,
    canRead: (BookFormat) -> Boolean = dev.kent.windbook.reader.formats.FormatCapabilities.BUILT_IN::canRead) {
    var confirmDisconnect by remember(state.resolvedFolderId) { mutableStateOf(false) }
    val results = remember(state.entries, state.query, state.options.extensions, state.showHidden) {
        state.entries.filter { (state.query.isBlank() || (it.relativePath.contains(state.query.trim(), true) || it.title.contains(state.query.trim(), true))) &&
            (state.showHidden || !it.hidden) &&
            it.format in state.options.extensions }
    }
    LazyColumn(Modifier.testTag("folder-list").fillMaxSize().windowInsetsPadding(TabScreenInsets).imePadding(),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = actions.back) { Text(stringResource(R.string.folder_back)) }
            Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.folder_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(state.locationHint, actions.onLocationEdited, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.folder_location)) }, singleLine = true, enabled = !state.busy)
            Text(stringResource(R.string.folder_grant_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = actions.pickFolder, enabled = !state.busy) {
                Text(stringResource(if (state.resolvedFolderId != null && !state.connected)
                    R.string.folder_reconnect else R.string.folder_choose))
            }
            Text(state.selectedLocation.ifEmpty { stringResource(R.string.folder_not_selected) },
                color = MaterialTheme.colorScheme.primary)
        }
        item {
            Text(stringResource(R.string.folder_saved), style = MaterialTheme.typography.titleMedium)
        }
        items(state.folders, key = { "folder-${it.id}" }) { folder ->
            OutlinedButton(onClick = { actions.selectFolder(folder.id) }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth()) {
                Text(folder.displayLocation + if (!folder.connected) " · " + stringResource(R.string.folder_disconnected) else "")
            }
        }
        item {
            Row(Modifier.fillMaxWidth().toggleable(state.options.recursive, role = Role.Checkbox) {
                actions.setRecursive(it)
            }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.options.recursive, null)
                Text(stringResource(R.string.folder_recursive))
            }
            Text(stringResource(R.string.folder_extensions), style = MaterialTheme.typography.titleMedium)
            Row {
                TextButton(onClick = { actions.setExtensions(BookFormat.entries.toSet()) }) {
                    Text(stringResource(R.string.folder_all))
                }
                TextButton(onClick = { actions.setExtensions(emptySet()) }) {
                    Text(stringResource(R.string.folder_clear))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BookFormat.entries.forEach { format ->
                    FilterChip(selected = format in state.options.extensions, onClick = {
                        actions.toggleExtension(format)
                    }, label = { Text(".${format.extension}") })
                }
            }
            Text(stringResource(R.string.folder_direct), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = actions.scan, enabled = state.canScan && state.progress.state != "RUNNING") {
                    Text(stringResource(R.string.folder_scan))
                }
                OutlinedButton(onClick = actions.stop, enabled = state.progress.state == "RUNNING") {
                    Text(stringResource(R.string.folder_stop))
                }
            }
            state.message?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        }
        item {
            HorizontalDivider()
            Text(stringResource(R.string.folder_results), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.folder_counts, state.progress.examined, state.progress.matched,
                state.progress.added, state.progress.known, state.progress.unreadable + state.progress.directoryErrors))
            Text(stringResource(when (state.progress.state) {
                "RUNNING" -> R.string.folder_running
                "COMPLETE" -> R.string.folder_complete
                "INCOMPLETE", "INTERRUPTED" -> R.string.folder_incomplete
                else -> R.string.folder_ready
            }))
            if (state.progress.state == "RUNNING") Text(state.progress.currentPath,
                style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth().toggleable(state.showHidden, role = Role.Checkbox, onValueChange = actions.showHidden), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.showHidden, null)
                Text(stringResource(R.string.folder_show_hidden))
            }
            OutlinedTextField(state.query, actions.setQuery, Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.folder_search)) }, singleLine = true)
        }
        if (results.isEmpty()) item { Text(stringResource(R.string.folder_empty)) }
        items(results, key = { "entry-${it.sourceId}" }) { entry ->
            Column(Modifier.fillMaxWidth().clickable(enabled = !entry.hidden && canRead(entry.format)) { actions.openBook(entry.bookId) }.padding(vertical = 8.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(entry.relativePath, style = MaterialTheme.typography.bodySmall)
                if (entry.hidden) TextButton(onClick = { actions.restore(entry.bookId) }) {
                    Text(stringResource(R.string.folder_restore))
                }
                Text(if (!canRead(entry.format))
                    stringResource(R.string.folder_unavailable) else stringResource(R.string.folder_open),
                    color = MaterialTheme.colorScheme.primary)
                if (entry.availability.name !in setOf("AVAILABLE", "UNVERIFIED"))
                    Text(stringResource(R.string.folder_restore_hint), style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
        if (state.resolvedFolderId != null && state.connected) item {
            TextButton(onClick = { confirmDisconnect = true }) { Text(stringResource(R.string.folder_disconnect)) }
        }
    }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false },
        title = { Text(stringResource(R.string.folder_disconnect)) },
        text = { Text(pluralStringResource(R.plurals.folder_disconnect_count, state.entries.map { it.bookId }.distinct().size, state.entries.map { it.bookId }.distinct().size)) },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; actions.disconnect() }) {
            Text(stringResource(R.string.folder_disconnect))
        } }, dismissButton = { TextButton(onClick = { confirmDisconnect = false }) {
            Text(stringResource(android.R.string.cancel))
        } })
}
