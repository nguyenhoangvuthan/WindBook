package dev.kent.windbook.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kent.windbook.R
import dev.kent.windbook.core.data.LibraryPreferences
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val HINT_TIMEOUT_MS = 8_000L

/** A small card, shown once per install, that explains the tap zones; it goes away on its button or after eight seconds. */
@Composable
internal fun TapHint(visible: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (!visible) return
    val latest by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        delay(HINT_TIMEOUT_MS)
        latest()
    }
    Surface(
        modifier = modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.reader_tap_hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.reader_tap_hint_ok), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}

/** Remembers whether the tap-zone hint was already shown; `null` until the stored value is read. */
@HiltViewModel
internal class TapHintViewModel @Inject constructor(private val preferences: LibraryPreferences) : ViewModel() {
    val seen: StateFlow<Boolean?> = preferences.tapHintSeen.map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markSeen() {
        viewModelScope.launch { preferences.setTapHintSeen() }
    }
}
