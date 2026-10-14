package dev.kent.windbook.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.library.components.BookCell
import dev.kent.windbook.library.components.ContinueReadingCard
import dev.kent.windbook.library.components.FilterRow
import dev.kent.windbook.library.components.LibraryHeader
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.icons.WindBookIcons

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

@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            if (!state.isLibraryEmpty) {
                LibraryHeader(
                    bookCount = state.bookCount,
                    query = state.query,
                    sort = state.sort,
                    onQueryChange = actions.onQueryChange,
                    onSortChange = actions.onSortChange,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        },
        floatingActionButton = {
            if (!state.isLibraryEmpty) AddBooksFab(importing = state.isImporting, onClick = actions.onImportClick)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLibraryEmpty) {
            EmptyState(
                title = stringResource(R.string.tab_library),
                body = stringResource(R.string.empty_library),
                actionLabel = stringResource(R.string.add_books),
                onAction = actions.onImportClick,
                modifier = Modifier.padding(padding),
            )
        } else {
            LibraryContent(state, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AddBooksFab(importing: Boolean, onClick: () -> Unit) {
    val label = stringResource(if (importing) R.string.importing else R.string.add_books)
    FloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.tertiary,
        contentColor = MaterialTheme.colorScheme.onTertiary,
        modifier = Modifier.semantics { contentDescription = label },
    ) {
        if (importing) {
            CircularProgressIndicator(Modifier.size(24.dp), color = LocalContentColor.current, strokeWidth = 2.dp)
        } else {
            Icon(WindBookIcons.Add, contentDescription = null)
        }
    }
}

@Composable
private fun LibraryContent(state: LibraryUiState, actions: LibraryActions, modifier: Modifier = Modifier) {
    val columns = if (state.layout == LibraryLayout.GRID) GridCells.Adaptive(minSize = 100.dp) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(state.filter, state.layout, actions.onFilterChange, actions.onLayoutChange)
        }
        state.continueReading?.takeIf { state.query.isBlank() }?.let { current ->
            item(span = { GridItemSpan(maxLineSpan) }) { ContinueReadingCard(current, actions.onOpenBook) }
        }
        items(state.items, key = { it.book.id }) { item -> BookCell(item, state.layout, actions) }
    }
}
