package dev.kent.windbook.folders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.data.folders.folderScanFormats
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ScanOptions

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FolderScanOptions(options: ScanOptions, onRecursiveChange: (Boolean) -> Unit,
    onToggleExtension: (BookFormat) -> Unit, onExtensionsChange: (Set<BookFormat>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(options.recursive, role = Role.Checkbox,
            onValueChange = onRecursiveChange), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.folder_recursive), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f))
            Checkbox(options.recursive, null)
        }
        Text(stringResource(R.string.folder_extensions), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            folderScanFormats.forEach { format ->
                FilterChip(selected = format in options.extensions, onClick = { onToggleExtension(format) },
                    label = { Text(".${format.extension}") })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.folder_all), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onExtensionsChange(folderScanFormats) }
                    .wrapContentHeight(Alignment.CenterVertically))
            Text(stringResource(R.string.folder_clear), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { onExtensionsChange(emptySet()) }
                    .wrapContentHeight(Alignment.CenterVertically))
        }
    }
}
