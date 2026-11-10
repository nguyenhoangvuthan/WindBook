package dev.kent.windbook.reader

import androidx.compose.foundation.interaction.*
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kent.windbook.R

@Composable
internal fun ReaderPositionSlider(fraction: Double,enabled: Boolean,onPreview: (Double?)->Unit = {},onSeek: (Double)->Unit) {
    val current=if(fraction.isFinite()) fraction.coerceIn(0.0,1.0).toFloat() else 0f
    var draft by remember {mutableFloatStateOf(current)}
    var changed by remember {mutableStateOf(false)}
    var cancelled by remember {mutableStateOf(false)}
    val interactions=remember {MutableInteractionSource()}
    val dragged by interactions.collectIsDraggedAsState()
    val seek by rememberUpdatedState(onSeek)
    LaunchedEffect(current,dragged) {if(!dragged) draft=current}
    LaunchedEffect(interactions) {
        interactions.interactions.collect { event ->
            when(event) {
                is DragInteraction.Start -> cancelled=false
                is DragInteraction.Cancel -> {cancelled=true;changed=false;draft=current;onPreview(null)}
            }
        }
    }
    val label=stringResource(R.string.reader_position)
    Slider(value=draft,onValueChange={cancelled=false;draft=it;changed=true;onPreview(it.toDouble())},enabled=enabled,
        onValueChangeFinished={if(changed && !cancelled) seek(draft.toDouble());changed=false;onPreview(null)},
        interactionSource=interactions,modifier=Modifier.semantics {contentDescription=label})
}
