package dev.kent.windbook.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R

internal const val READER_SLIDER_THUMB_TAG="reader-slider-thumb"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderPositionSlider(fraction: Double,enabled: Boolean,modifier: Modifier = Modifier,onPreview: (Double?)->Unit = {},onSeek: (Double)->Unit) {
    val current=if(fraction.isFinite()) fraction.coerceIn(0.0,1.0).toFloat() else 0f
    var draft by remember {mutableStateOf<Float?>(null)}
    val interactions=remember {MutableInteractionSource()}
    val seek by rememberUpdatedState(onSeek)
    val preview by rememberUpdatedState(onPreview)
    LaunchedEffect(interactions) {
        interactions.interactions.collect { event ->
            if(event is DragInteraction.Cancel) {draft=null;preview(null)}
        }
    }
    DisposableEffect(Unit) {onDispose {preview(null)}}
    val label=stringResource(R.string.reader_position)
    val active=MaterialTheme.colorScheme.primary
    val inactive=MaterialTheme.colorScheme.outlineVariant
    val rtl=LocalLayoutDirection.current==LayoutDirection.Rtl
    Slider(value=draft ?: current,onValueChange={draft=it;preview(it.toDouble())},enabled=enabled,
        onValueChangeFinished={val target=draft;draft=null;preview(null);if(target!=null) seek(target.toDouble())},
        interactionSource=interactions,
        thumb={Box(Modifier.size(12.dp).testTag(READER_SLIDER_THUMB_TAG).background(if(enabled) active else inactive,CircleShape))},
        track={state ->Canvas(Modifier.fillMaxWidth().height(2.dp)) {
            val start=if(rtl) size.width else 0f
            val end=if(rtl) 0f else size.width
            drawLine(inactive,Offset(start,center.y),Offset(end,center.y),strokeWidth=size.height,cap=StrokeCap.Round)
            drawLine(if(enabled) active else inactive,Offset(start,center.y),Offset(start+(end-start)*state.value,center.y),strokeWidth=size.height,cap=StrokeCap.Round)
        }},
        modifier=modifier.heightIn(min=48.dp).semantics {contentDescription=label})
}
