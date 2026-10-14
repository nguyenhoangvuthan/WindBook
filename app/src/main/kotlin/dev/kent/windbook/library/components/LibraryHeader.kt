package dev.kent.windbook.library.components

import androidx.annotation.StringRes
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.LibraryLayout
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.ui.components.Pill
import dev.kent.windbook.ui.icons.WindBookIcons
import dev.kent.windbook.ui.theme.PillShape

const val LIBRARY_SEARCH_TAG = "library-search"
const val LIBRARY_FILTERS_TAG = "library-filters"

/** "Library │ 12 books", then the search pill and the sort pill (reference image 01). */
@Composable
internal fun LibraryHeader(
    bookCount: Int,
    query: String,
    sort: LibrarySort,
    layout: LibraryLayout,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onLayoutChange: (LibraryLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Row {
            Text(
                stringResource(R.string.tab_library),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.alignByBaseline().semantics { heading() },
            )
            Text(
                "│",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                // Decorative: TalkBack would read it as "box drawings light vertical".
                modifier = Modifier.alignByBaseline().padding(horizontal = 10.dp).clearAndSetSemantics {},
            )
            Text(
                pluralStringResource(R.plurals.book_count, bookCount, bookCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SearchPill(query, onQueryChange, Modifier.weight(1f))
            Spacer(Modifier.width(4.dp))
            SortPill(sort, onSortChange)
            // Lives here, not in the filter row, so all four filter pills fit on a phone.
            IconButton(onClick = { onLayoutChange(if (layout == LibraryLayout.GRID) LibraryLayout.LIST else LibraryLayout.GRID) }) {
                Icon(
                    if (layout == LibraryLayout.GRID) WindBookIcons.List else WindBookIcons.Grid,
                    contentDescription = stringResource(R.string.cd_change_layout),
                )
            }
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

/** Status filter pills. The row scrolls; a soft fade marks the side that still has hidden pills. */
@Composable
internal fun FilterRow(selected: StatusFilter, onSelect: (StatusFilter) -> Unit) {
    val scroll = rememberScrollState()
    Row(
        Modifier
            .fillMaxWidth()
            .testTag(LIBRARY_FILTERS_TAG)
            .edgeFade(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusFilter.entries.forEach { filter ->
            Pill(stringResource(filter.labelRes()), onClick = { onSelect(filter) }, selected = filter == selected)
        }
    }
}

/** Fades the left/right edge only while there is more content to scroll to on that side. */
private fun Modifier.edgeFade(scroll: ScrollState, width: Dp = 24.dp): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = width.toPx()
            if (scroll.canScrollForward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - w, endX = size.width),
                    topLeft = Offset(size.width - w, 0f),
                    size = Size(w, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (scroll.canScrollBackward) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = w),
                    size = Size(w, size.height),
                    blendMode = BlendMode.DstIn,
                )
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
