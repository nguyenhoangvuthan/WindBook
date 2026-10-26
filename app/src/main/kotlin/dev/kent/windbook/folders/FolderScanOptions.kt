package dev.kent.windbook.folders

import androidx.compose.foundation.layout.*
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
    Row(Modifier.fillMaxWidth().toggleable(options.recursive, role = Role.Checkbox,
        onValueChange = onRecursiveChange), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(options.recursive, null)
        Text(stringResource(R.string.folder_recursive))
    }
    Text(stringResource(R.string.folder_extensions), style = MaterialTheme.typography.titleMedium)
    Row {
        TextButton(onClick = { onExtensionsChange(folderScanFormats) }) { Text(stringResource(R.string.folder_all)) }
        TextButton(onClick = { onExtensionsChange(emptySet()) }) { Text(stringResource(R.string.folder_clear)) }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        folderScanFormats.forEach { format ->
            FilterChip(selected = format in options.extensions, onClick = { onToggleExtension(format) },
                label = { Text(".${format.extension}") })
        }
    }
}
