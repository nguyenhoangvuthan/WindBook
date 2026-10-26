package dev.kent.windbook.folders

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.provider.DocumentsContract
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
    var activePickerId by rememberSaveable { mutableStateOf<Long?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        activePickerId?.let { requestId ->
            val data = result.data
            val uri = data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null)
                viewModel.onTreeGranted(uri, data.flags, requestId)
            else viewModel.onPickerCancelled(requestId)
        }
        activePickerId = null
    }
    LaunchedEffect(state.pickerRequest) {
        state.pickerRequest?.let { request ->
            if (activePickerId != request.id) {
                activePickerId = request.id
                picker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION).apply {
                        request.initialUri?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
                    })
            }
        }
    }
    val renderedFolderId = state.resolvedFolderId
    FolderScreen(state, FolderActions(
        onLocationEdited = viewModel::onLocationEdited,
        setOptions = { viewModel.setOptions(it, renderedFolderId) },
        scan = { viewModel.scan(renderedFolderId) }, stop = { viewModel.stop(renderedFolderId) }, setQuery = viewModel::setQuery,
        disconnect = { viewModel.disconnect(renderedFolderId) },
        pickFolder = { viewModel.requestFolder(renderedFolderId) },
        selectFolder = { viewModel.selectFolder(it) },
        openBook = { viewModel.openBook(renderedFolderId, it, onOpenBook) },
        restore = { viewModel.restore(it, renderedFolderId) }, back = onBack, showHidden = viewModel::showHidden,
        setRecursive = { viewModel.setRecursive(it, renderedFolderId) }, toggleExtension = { viewModel.toggleExtension(it, renderedFolderId) },
        setExtensions = { viewModel.setExtensions(it, renderedFolderId) }, setSort = viewModel::setSort,
    ), canRead = viewModel::canRead)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderScreen(state: FolderUiState, actions: FolderActions,
    canRead: (BookFormat) -> Boolean = dev.kent.windbook.reader.formats.FormatCapabilities.BUILT_IN::canRead) {
    var savedMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var confirmDisconnect by remember(state.resolvedFolderId) { mutableStateOf(false) }
    val results = remember(state.entries, state.query, state.options.extensions, state.showHidden, state.sort) {
        state.entries.filter { (state.query.isBlank() || (it.relativePath.contains(state.query.trim(), true) || it.title.contains(state.query.trim(), true))) &&
            (state.showHidden || !it.hidden) &&
            it.format in state.options.extensions }.sortedWith(state.sort.comparator)
    }
    LazyColumn(Modifier.testTag("folder-list").fillMaxSize().windowInsetsPadding(TabScreenInsets).imePadding(),
        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = actions.back) { Text(stringResource(R.string.folder_back)) }
            Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.folder_intro), style = MaterialTheme.typography.bodyMedium)
        }
        item {
            Box {
                OutlinedButton(onClick = { savedMenu = true }, enabled = !state.busy && state.folders.isNotEmpty()) {
                    Text(stringResource(R.string.folder_saved))
                }
                DropdownMenu(expanded = savedMenu, onDismissRequest = { savedMenu = false }) {
                    state.folders.forEach { folder ->
                        val isSelected = folder.id == state.resolvedFolderId
                        DropdownMenuItem(text = {
                            Text(folder.displayLocation + if (!folder.connected)
                                " · " + stringResource(R.string.folder_disconnected) else "")
                        }, onClick = {
                            savedMenu = false
                            actions.selectFolder(folder.id)
                        }, enabled = !state.busy, modifier = Modifier
                            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .semantics { selected = isSelected })
                    }
                }
            }
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
            Text(stringResource(if (state.resolvedFolderId == null) R.string.folder_new_options_hint else R.string.folder_options_hint),
                style = MaterialTheme.typography.bodySmall)
            FolderScanOptions(state.options, actions.setRecursive, actions.toggleExtension, actions.setExtensions)
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
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Text(stringResource(R.string.folder_sort, stringResource(state.sort.labelRes())))
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    FolderSort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(stringResource(sort.labelRes())) }, onClick = {
                            actions.setSort(sort)
                            sortMenu = false
                        })
                    }
                }
            }
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

private fun FolderSort.labelRes(): Int = when (this) {
    FolderSort.TITLE_ASC -> R.string.folder_sort_title_asc
    FolderSort.TITLE_DESC -> R.string.folder_sort_title_desc
    FolderSort.ADDED_NEWEST -> R.string.folder_sort_added_newest
    FolderSort.ADDED_OLDEST -> R.string.folder_sort_added_oldest
}
