package dev.kent.windbook.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.kent.windbook.R
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.ui.icons.WindBookIcons
import java.io.File
import kotlin.math.roundToInt

data class LibraryActions(
    val onOpenBook: (String) -> Unit,
    val onImportClick: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onFilterChange: (StatusFilter) -> Unit,
    val onSortChange: (LibrarySort) -> Unit,
    val onLayoutChange: (LibraryLayout) -> Unit,
    val onSetStatus: (String, ReadingStatus) -> Unit,
    val onDelete: (String) -> Unit,
)

private val IMPORT_MIME_TYPES = arrayOf("application/epub+zip", "application/pdf", "application/octet-stream")

@Composable
fun LibraryRoute(onOpenBook: (String) -> Unit, viewModel: LibraryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.import(uris)
    }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            when (message) {
                is LibraryMessage.Imported ->
                    snackbar.showSnackbar(resources.getString(R.string.imported_count, message.count))
                is LibraryMessage.AlreadyInLibrary -> {
                    val result = snackbar.showSnackbar(
                        resources.getString(R.string.already_in_library),
                        actionLabel = resources.getString(R.string.open),
                    )
                    if (result == SnackbarResult.ActionPerformed) onOpenBook(message.bookId)
                }
                is LibraryMessage.ImportFailed -> snackbar.showSnackbar(
                    resources.getString(
                        R.string.import_failed,
                        message.name ?: resources.getString(R.string.unnamed_file),
                        resources.getString(message.error.messageRes()),
                    ),
                )
            }
        }
    }

    LibraryScreen(
        state = state,
        snackbarHostState = snackbar,
        actions = LibraryActions(
            onOpenBook = onOpenBook,
            onImportClick = { picker.launch(IMPORT_MIME_TYPES) },
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onSortChange = viewModel::onSortChange,
            onLayoutChange = viewModel::onLayoutChange,
            onSetStatus = viewModel::setStatus,
            onDelete = viewModel::delete,
        ),
    )
}

@StringRes
private fun ImportError.messageRes(): Int = when (this) {
    ImportError.UNSUPPORTED_FORMAT -> R.string.err_unsupported
    ImportError.NOT_AN_EPUB -> R.string.err_not_epub
    ImportError.CORRUPT -> R.string.err_corrupt
    ImportError.TOO_LARGE -> R.string.err_too_large
    ImportError.TOO_LARGE_UNCOMPRESSED,
    ImportError.TOO_MANY_ENTRIES,
    ImportError.UNSAFE_PATHS -> R.string.err_unsafe
    ImportError.NO_SPACE -> R.string.err_no_space
    ImportError.READ_FAILED -> R.string.err_read_failed
    ImportError.UNREADABLE -> R.string.err_unreadable
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    var searching by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = state.query,
                            onValueChange = actions.onQueryChange,
                            placeholder = { Text(stringResource(R.string.search_books)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) actions.onQueryChange("")
                        searching = !searching
                    }) {
                        Icon(
                            if (searching) WindBookIcons.Close else WindBookIcons.Search,
                            contentDescription = stringResource(if (searching) R.string.cd_close_search else R.string.cd_search),
                        )
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(WindBookIcons.Sort, contentDescription = stringResource(R.string.cd_sort))
                        }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text((if (sort == state.sort) "✓ " else "   ") + stringResource(sort.labelRes())) },
                                    onClick = { actions.onSortChange(sort); sortMenu = false },
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        actions.onLayoutChange(if (state.layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID)
                    }) {
                        Icon(
                            if (state.layout == LibraryLayout.GRID) WindBookIcons.List else WindBookIcons.Grid,
                            contentDescription = stringResource(R.string.cd_change_layout),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.isLibraryEmpty) {
                ExtendedFloatingActionButton(
                    onClick = actions.onImportClick,
                    icon = { Icon(WindBookIcons.Add, contentDescription = null) },
                    text = { Text(stringResource(if (state.isImporting) R.string.importing else R.string.add_books)) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLibraryEmpty) {
            EmptyLibrary(onImportClick = actions.onImportClick, modifier = Modifier.padding(padding))
        } else {
            LibraryContent(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun EmptyLibrary(onImportClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_library), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onImportClick) { Text(stringResource(R.string.add_books)) }
    }
}

@Composable
private fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val columns = if (state.layout == LibraryLayout.GRID) GridCells.Adaptive(minSize = 104.dp) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.continueReading?.takeIf { state.query.isBlank() }?.let { current ->
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(current, actions.onOpenBook) }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { FilterRow(state.filter, actions.onFilterChange) }
        items(state.items, key = { it.book.id }) { item ->
            BookCell(item, state.layout, actions)
        }
    }
}

@Composable
private fun ContinueReadingCard(item: LibraryItem, onOpen: (String) -> Unit) {
    Card(onClick = { onOpen(item.book.id) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(item, Modifier.width(56.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.continue_reading), style = MaterialTheme.typography.labelLarge)
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { item.progression.toFloat() }, modifier = Modifier.fillMaxWidth())
                Text(percent(item.progression), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FilterRow(selected: StatusFilter, onSelect: (StatusFilter) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCell(item: LibraryItem, layout: LibraryLayout, actions: LibraryActions) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Box {
        val clickable = Modifier.combinedClickable(
            onClick = { actions.onOpenBook(item.book.id) },
            onLongClick = { menu = true },
        )
        if (layout == LibraryLayout.GRID) {
            Column(clickable) {
                Cover(item, Modifier.fillMaxWidth())
                Text(item.book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.progression > 0) Text(percent(item.progression), style = MaterialTheme.typography.labelSmall)
            }
        } else {
            Row(clickable.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Cover(item, Modifier.width(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    item.book.author?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                }
                Text(percent(item.progression), style = MaterialTheme.typography.labelMedium)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(
                ReadingStatus.TO_READ to R.string.mark_to_read,
                ReadingStatus.READING to R.string.mark_reading,
                ReadingStatus.FINISHED to R.string.mark_finished,
            ).filter { it.first != item.book.status }.forEach { (status, label) ->
                DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                    actions.onSetStatus(item.book.id, status)
                    menu = false
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.delete_book)) }, onClick = {
                menu = false
                confirmDelete = true
            })
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, item.book.title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = { actions.onDelete(item.book.id); confirmDelete = false }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Cover(item: LibraryItem, modifier: Modifier) {
    val shape = RoundedCornerShape(6.dp)
    val coverModifier = modifier.aspectRatio(2f / 3f).clip(shape)
    val coverPath = item.book.coverPath
    if (coverPath != null) {
        AsyncImage(
            model = File(coverPath),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = coverModifier,
        )
    } else {
        Box(coverModifier.background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Text(
                item.book.title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun percent(progression: Double) = "${(progression * 100).roundToInt()}%"

@StringRes
private fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.sort_recent
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.AUTHOR -> R.string.sort_author
    LibrarySort.ADDED -> R.string.sort_added
    LibrarySort.PROGRESS -> R.string.sort_progress
}

@StringRes
private fun StatusFilter.labelRes(): Int = when (this) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.READING -> R.string.filter_reading
    StatusFilter.TO_READ -> R.string.filter_to_read
    StatusFilter.FINISHED -> R.string.filter_finished
}
