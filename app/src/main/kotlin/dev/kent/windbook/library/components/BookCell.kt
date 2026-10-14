package dev.kent.windbook.library.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.library.LibraryActions
import dev.kent.windbook.ui.components.BookCover
import dev.kent.windbook.ui.components.ProgressLine
import dev.kent.windbook.ui.components.formatPercent
import dev.kent.windbook.ui.components.uppercaseForUi
import dev.kent.windbook.ui.icons.WindBookIcons

@Composable
internal fun ContinueReadingCard(item: LibraryItem, onOpen: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = { onOpen(item.book.id) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BookCover(item.book.id, item.book.title, item.book.coverPath, Modifier.width(52.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.continue_reading).uppercaseForUi(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    color = colors.onSurfaceVariant,
                )
                Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.book.author?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgressLine(item.progression, Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(formatPercent(item.progression), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCell(item: LibraryItem, layout: LibraryLayout, actions: LibraryActions) {
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val book = item.book
    val clickable = Modifier.combinedClickable(
        onClick = { actions.onOpenBook(book.id) },
        onLongClick = { menu = true },
    )
    Box {
        if (layout == LibraryLayout.GRID) {
            Column(clickable) {
                Box {
                    BookCover(book.id, book.title, book.coverPath, Modifier.fillMaxWidth(), titleEndInset = 28.dp)
                    MoreButton(book.title, onClick = { menu = true }, modifier = Modifier.align(Alignment.TopEnd))
                }
                if (item.progression > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(item.progression)
                }
                Spacer(Modifier.height(6.dp))
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BookCaption(item)
            }
        } else {
            Row(clickable.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(book.id, book.title, book.coverPath, Modifier.width(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    BookCaption(item)
                    if (item.progression > 0) {
                        Spacer(Modifier.height(6.dp))
                        ProgressLine(item.progression)
                    }
                }
                MoreButton(book.title, onClick = { menu = true })
            }
        }
        BookMenu(
            expanded = menu,
            item = item,
            onDismiss = { menu = false },
            onSetStatus = actions.onSetStatus,
            onDeleteRequest = {
                menu = false
                confirmDelete = true
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_title, book.title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete(book.id)
                    confirmDelete = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun BookCaption(item: LibraryItem) {
    val finished = item.book.status == ReadingStatus.FINISHED
    val text = if (finished) stringResource(R.string.finished_caption) else item.book.author ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (finished) {
            Icon(WindBookIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MoreButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                WindBookIcons.MoreHoriz,
                contentDescription = stringResource(R.string.cd_more_options, title),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun BookMenu(
    expanded: Boolean,
    item: LibraryItem,
    onDismiss: () -> Unit,
    onSetStatus: (String, ReadingStatus) -> Unit,
    onDeleteRequest: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        listOf(
            ReadingStatus.TO_READ to R.string.mark_to_read,
            ReadingStatus.READING to R.string.mark_reading,
            ReadingStatus.FINISHED to R.string.mark_finished,
        ).filter { it.first != item.book.status }.forEach { (status, label) ->
            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                onSetStatus(item.book.id, status)
                onDismiss()
            })
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.delete_book)) }, onClick = onDeleteRequest)
    }
}
