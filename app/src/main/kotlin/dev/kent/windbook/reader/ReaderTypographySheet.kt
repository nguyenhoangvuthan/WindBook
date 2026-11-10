package dev.kent.windbook.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.*
import kotlin.math.roundToInt

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
            if(!globalOnly) Row {
                TextButton(onClick={editingGlobal=false}) {Text(stringResource(R.string.reader_scope_book),color=if(!editingGlobal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
                TextButton(onClick={editingGlobal=true}) {Text(stringResource(R.string.reader_scope_global),color=if(editingGlobal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)}
            }
            Text(stringResource(R.string.reader_scope_hint),style=MaterialTheme.typography.bodySmall)
            if(!editingGlobal) Text(stringResource(R.string.reader_inherit_hint),style=MaterialTheme.typography.bodySmall)
            if(!reflowable && !editingGlobal) Text(stringResource(R.string.reader_fixed_appearance)) else {
                Text(stringResource(R.string.reader_font),style=MaterialTheme.typography.titleSmall)
                FlowRow {
                    listOf(ReaderFont.PUBLISHER to R.string.reader_font_publisher,ReaderFont.LITERATA to R.string.reader_font_literata,ReaderFont.SERIF to R.string.reader_font_serif,ReaderFont.SANS to R.string.reader_font_sans).forEach {(font,label)->
                        FilterChip(selected=(effective.font ?: ReaderFont.PUBLISHER)==font,onClick={change {it.copy(font=font)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp))
                    }
                }
                Text(stringResource(R.string.reader_text_size)+" · "+(effective.size?.let {"$it%"} ?: stringResource(R.string.reader_font_publisher)))
                Slider(value=(effective.size ?: 100).toFloat(),valueRange=80f..200f,steps=23,onValueChange={change {current -> current.copy(size=it.roundToInt())}})
                Text(stringResource(R.string.reader_line_height)+" · "+(effective.lineHeight?.takeIf {it>0}?.toString() ?: stringResource(R.string.reader_font_publisher)))
                TextButton(onClick={change {it.copy(lineHeight=0.0)}}) {Text(stringResource(R.string.reader_font_publisher))}
                Slider(value=(effective.lineHeight?.takeIf {it>0} ?: 1.5).toFloat(),valueRange=1.2f..2f,steps=7,onValueChange={change {current -> current.copy(lineHeight=(it*10).roundToInt()/10.0)}})
                Text(stringResource(R.string.reader_margins),style=MaterialTheme.typography.titleSmall)
                FlowRow {listOf(R.string.reader_margin_narrow,R.string.reader_margin_normal,R.string.reader_margin_wide).forEachIndexed {index,label->
                    FilterChip(selected=(effective.margin ?: 1)==index,onClick={change {it.copy(margin=index)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp))
                }}
            }
            Text(stringResource(R.string.reader_colors),style=MaterialTheme.typography.titleSmall)
            FlowRow {listOf(ReaderColors.SYSTEM to R.string.reader_color_system,ReaderColors.PAPER to R.string.reader_color_paper,ReaderColors.SEPIA to R.string.reader_color_sepia,ReaderColors.DARK to R.string.reader_color_dark).forEach {(color,label)->
                FilterChip(selected=(effective.colors ?: ReaderColors.SYSTEM)==color,onClick={change {it.copy(colors=color)}},label={Text(stringResource(label))},modifier=Modifier.padding(end=8.dp))
            }}
            TextButton(onClick={change {ReaderTypography()}}) {Text(stringResource(if(editingGlobal) R.string.reader_reset_defaults else R.string.reader_reset_global))}
        }
    }
}
