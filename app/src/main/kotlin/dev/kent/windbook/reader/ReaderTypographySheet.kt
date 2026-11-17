package dev.kent.windbook.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import kotlin.math.roundToInt

internal const val LINE_HEIGHT_SLIDER_TAG="line-height-slider"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ReaderTypographySheet(book: ReaderTypography,global: ReaderTypography,onDismiss: ()->Unit,
    onChange: ((ReaderTypography)->ReaderTypography,Boolean)->Unit,reflowable: Boolean=true,globalOnly: Boolean=false) {
    var editingGlobal by rememberSaveable {mutableStateOf(globalOnly)}
    val value=if(editingGlobal) global else book
    val effective=if(editingGlobal) value else value.resolveOver(global)
    fun change(edit: (ReaderTypography)->ReaderTypography) = onChange(edit,editingGlobal)
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.reader_appearance),style=MaterialTheme.typography.titleLarge)
            if(!globalOnly) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(false to R.string.reader_scope_book,true to R.string.reader_scope_global).forEachIndexed {index,(global,label)->
                    SegmentedButton(selected=editingGlobal==global,onClick={editingGlobal=global},shape=SegmentedButtonDefaults.itemShape(index,2)) {Text(stringResource(label))}
                }
            }
            Text(stringResource(if(editingGlobal) R.string.reader_scope_hint_global else R.string.reader_scope_hint_book),style=MaterialTheme.typography.bodySmall)
            if(!reflowable && !editingGlobal) Text(stringResource(R.string.reader_fixed_appearance)) else {
                val original=effective.originalLayout==true
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=original,role=Role.Switch,onValueChange={on->change {it.copy(originalLayout=on)}}),
                    verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.reader_keep_original_layout))
                        Text(stringResource(R.string.reader_keep_original_layout_hint),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked=original,onCheckedChange=null)
                }
                Text(stringResource(R.string.reader_font),style=MaterialTheme.typography.titleSmall)
                FlowRow {
                    listOf(ReaderFont.PUBLISHER to R.string.reader_font_publisher,ReaderFont.LITERATA to R.string.reader_font_literata,ReaderFont.SERIF to R.string.reader_font_serif,ReaderFont.SANS to R.string.reader_font_sans).forEach {(font,label)->
                        FilterChip(selected=(effective.font ?: ReaderFont.PUBLISHER)==font,onClick={change {it.copy(font=font)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp))
                    }
                }
                Text(stringResource(R.string.reader_text_size)+" · "+(effective.size?.let {"$it%"} ?: stringResource(R.string.reader_value_publisher)))
                // 10 % steps from 80 to 200; the label above always says the number.
                Slider(value=(effective.size ?: 100).toFloat(),valueRange=80f..200f,steps=11,onValueChange={change {current -> current.copy(size=(it/10).roundToInt()*10)}})
                // Clean layout always has a number (the app default 1.5); the book's own value only exists when keeping the original.
                val lineHeight=effective.lineHeight?.takeIf {it>0}
                val shownLineHeight=if(original) null else (lineHeight ?: 1.5)
                Text(stringResource(R.string.reader_line_height)+" · "+(shownLineHeight?.toString() ?: stringResource(R.string.reader_value_publisher)))
                Slider(value=(shownLineHeight ?: 1.5).toFloat(),valueRange=1.2f..2f,steps=7,enabled=!original,modifier=Modifier.testTag(LINE_HEIGHT_SLIDER_TAG),
                    onValueChange={change {current -> current.copy(lineHeight=(it*10).roundToInt()/10.0)}})
                if(lineHeight!=null && !original) TextButton(onClick={change {it.copy(lineHeight=0.0)}}) {Text(stringResource(R.string.reader_line_height_reset))}
                Text(stringResource(R.string.reader_margins),style=MaterialTheme.typography.titleSmall)
                FlowRow {listOf(R.string.reader_margin_narrow,R.string.reader_margin_normal,R.string.reader_margin_wide).forEachIndexed {index,label->
                    FilterChip(selected=(effective.margin ?: 1)==index,onClick={change {it.copy(margin=index)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp))
                }}
            }
            Text(stringResource(R.string.reader_colors),style=MaterialTheme.typography.titleSmall)
            FlowRow {listOf(ReaderColors.SYSTEM to R.string.reader_color_system,ReaderColors.PAPER to R.string.reader_color_paper,ReaderColors.SEPIA to R.string.reader_color_sepia,ReaderColors.DARK to R.string.reader_color_dark).forEach {(color,label)->
                FilterChip(selected=(effective.colors ?: ReaderColors.SYSTEM)==color,onClick={change {it.copy(colors=color)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp),
                    leadingIcon={ColorSwatch(color)})
            }}
            TextButton(onClick={change {ReaderTypography()}}) {Text(stringResource(if(editingGlobal) R.string.reader_reset_defaults else R.string.reader_reset_global))}
        }
    }
}

/** Small preview disc so people pick a reading colour by look. System is an empty ring (it follows the theme). */
@Composable
private fun ColorSwatch(colors: ReaderColors) {
    val fill=when(colors) {
        ReaderColors.PAPER -> Color.White
        ReaderColors.SEPIA -> Color(0xFFFAF4E8)
        ReaderColors.DARK -> Color.Black
        ReaderColors.SYSTEM -> Color.Transparent
    }
    Box(Modifier.size(18.dp).clip(CircleShape).background(fill).border(1.dp,MaterialTheme.colorScheme.outline,CircleShape).testTag("swatch:${colors.name}"))
}
