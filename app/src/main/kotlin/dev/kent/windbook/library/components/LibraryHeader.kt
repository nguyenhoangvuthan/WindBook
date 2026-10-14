package dev.kent.windbook.library.components

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.ui.components.Pill
import dev.kent.windbook.ui.icons.WindBookIcons
import dev.kent.windbook.ui.theme.PillShape

const val LIBRARY_SEARCH_TAG = "library-search"

/** "Library │ 12 books", then the search pill and the sort pill (reference image 01). */
@Composable
internal fun LibraryHeader(
    bookCount: Int,
    query: String,
    sort: LibrarySort,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row {
            Text(
                stringResource(R.string.tab_library),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                "│",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.alignByBaseline().padding(horizontal = 10.dp),
            )
            Text(
                pluralStringResource(R.plurals.book_count, bookCount, bookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchPill(query, onQueryChange, Modifier.weight(1f))
            SortPill(sort, onSortChange)
        }
    }
}

@Composable
private fun SearchPill(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag(LIBRARY_SEARCH_TAG),
        placeholder = {
            Text(stringResource(R.string.search_books), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = { Icon(WindBookIcons.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(WindBookIcons.Close, contentDescription = stringResource(R.string.cd_clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = PillShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surfaceContainerLow,
            unfocusedContainerColor = colors.surfaceContainerLow,
            unfocusedBorderColor = colors.outlineVariant,
            focusedBorderColor = colors.primary,
        ),
    )
}

@Composable
private fun SortPill(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Pill(text = stringResource(R.string.sort), onClick = { open = true }, leadingIcon = WindBookIcons.Sort)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    trailingIcon = if (option == sort) {
                        { Icon(WindBookIcons.Check, contentDescription = stringResource(R.string.cd_selected)) }
                    } else {
                        null
                    },
                    onClick = {
                        onSortChange(option)
                        open = false
                    },
                )
            }
        }
    }
}

/** Status pills plus the grid/list toggle at the end of the row. */
@Composable
internal fun FilterRow(
    selected: StatusFilter,
    layout: LibraryLayout,
    onSelect: (StatusFilter) -> Unit,
    onLayoutChange: (LibraryLayout) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusFilter.entries.forEach { filter ->
                Pill(stringResource(filter.labelRes()), onClick = { onSelect(filter) }, selected = filter == selected)
            }
        }
        IconButton(onClick = { onLayoutChange(if (layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID) }) {
            Icon(
                if (layout == LibraryLayout.GRID) WindBookIcons.List else WindBookIcons.Grid,
                contentDescription = stringResource(R.string.cd_change_layout),
            )
        }
    }
}

@StringRes
internal fun LibrarySort.labelRes(): Int = when (this) {
    LibrarySort.RECENT -> R.string.sort_recent
    LibrarySort.TITLE -> R.string.sort_title
    LibrarySort.AUTHOR -> R.string.sort_author
    LibrarySort.ADDED -> R.string.sort_added
    LibrarySort.PROGRESS -> R.string.sort_progress
}

@StringRes
internal fun StatusFilter.labelRes(): Int = when (this) {
    StatusFilter.ALL -> R.string.filter_all
    StatusFilter.READING -> R.string.filter_reading
    StatusFilter.TO_READ -> R.string.filter_to_read
    StatusFilter.FINISHED -> R.string.filter_finished
}
