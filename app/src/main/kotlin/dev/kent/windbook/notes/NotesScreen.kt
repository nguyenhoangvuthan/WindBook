package dev.kent.windbook.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kent.windbook.R
import dev.kent.windbook.ui.components.EmptyState
import dev.kent.windbook.ui.components.ScreenTitle
import dev.kent.windbook.ui.components.TabScreenInsets

/** Placeholder until M4 adds highlights; the empty state is the designed one, not a "coming soon". */
@Composable
fun NotesScreen() {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(TabScreenInsets)) {
        ScreenTitle(stringResource(R.string.tab_notes))
        EmptyState(
            title = stringResource(R.string.notes_empty_title),
            body = stringResource(R.string.notes_empty_body),
        )
    }
}
