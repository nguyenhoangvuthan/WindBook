package dev.kent.windbook.reading

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.ui.components.BookCover
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.components.ProgressLine
import dev.kent.windbook.ui.components.ScreenTitle
import dev.kent.windbook.ui.components.TabScreenInsets
import dev.kent.windbook.ui.components.formatPercent

@Composable
fun ReadingRoute(onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit, viewModel: ReadingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ReadingScreen(state, onOpenBook, onBrowseLibrary)
}

@Composable
fun ReadingScreen(state: ReadingUiState, onOpenBook: (String) -> Unit, onBrowseLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(TabScreenInsets)) {
        ScreenTitle(stringResource(R.string.tab_reading))
        val current = state.current
        when {
            !state.loaded -> Unit
            current == null -> EmptyState(
                title = stringResource(R.string.reading_empty_title),
                body = stringResource(R.string.reading_empty_body),
                actionLabel = stringResource(R.string.go_to_library),
                onAction = onBrowseLibrary,
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = current.book.id) { CurrentBookCard(current, onOpenBook) }
                items(state.others, key = { it.book.id }) { ReadingRow(it, onOpenBook) }
            }
        }
    }
}

@Composable
private fun CurrentBookCard(item: LibraryItem, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { onOpen(item.book.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.padding(16.dp)) {
            BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(96.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(item.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
                item.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(12.dp))
                ProgressLine(item.progression)
                Spacer(Modifier.height(4.dp))
                Text(formatPercent(item.progression), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onOpen(item.book.id) }) { Text(stringResource(R.string.continue_button)) }
            }
        }
    }
}

@Composable
private fun ReadingRow(item: LibraryItem, onOpen: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onOpen(item.book.id) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(48.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            item.book.author?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressLine(item.progression, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(formatPercent(item.progression), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
