package dev.kent.windbook.settings

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kent.windbook.R
import dev.kent.windbook.folders.FolderScanOptions
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.ui.components.ScreenTitle
import dev.kent.windbook.ui.components.TabScreenInsets
import dev.kent.windbook.ui.components.uppercaseForUi

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel(), onFolders: () -> Unit = {}) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.refreshLanguage()
        onStopOrDispose { }
    }
    SettingsScreen(state, onLanguageChange = viewModel::setLanguage, onFolders = onFolders,
        onScanRecursiveChange = { viewModel.setScanRecursive(it) },
        onScanExtensionToggle = { viewModel.toggleScanExtension(it) },
        onScanExtensionsChange = { viewModel.setScanExtensions(it) })
}

@Composable
fun SettingsScreen(state: SettingsUiState, onLanguageChange: (AppLanguage) -> Unit,
    onFolders: () -> Unit = {}, onScanRecursiveChange: (Boolean) -> Unit = {},
    onScanExtensionToggle: (BookFormat) -> Unit = {}, onScanExtensionsChange: (Set<BookFormat>) -> Unit = {}) {
    var showLicences by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(TabScreenInsets)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenTitle(stringResource(R.string.tab_settings))

        SettingsGroup(stringResource(R.string.settings_language)) {
            AppLanguage.entries.forEach { language ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = language == state.language,
                            onClick = { onLanguageChange(language) },
                            role = Role.RadioButton,
                        )
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(language.labelRes), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = language == state.language, onClick = null)
                }
            }
        }

        SettingsGroup(stringResource(R.string.settings_folder_scanning)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_scan_defaults_hint), style = MaterialTheme.typography.bodySmall)
                FolderScanOptions(state.scanOptions, onScanRecursiveChange, onScanExtensionToggle, onScanExtensionsChange)
                Text(stringResource(R.string.folder_title), style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onFolders)
                        .heightIn(min = 48.dp).padding(vertical = 12.dp))
            }
        }

        SettingsGroup(stringResource(R.string.settings_storage)) {
            ValueRow(stringResource(R.string.storage_books), state.bookCount.toString())
            ValueRow(stringResource(R.string.storage_files), Formatter.formatShortFileSize(context, state.totalBytes))
            ValueRow(stringResource(R.string.storage_free), Formatter.formatShortFileSize(context, state.freeBytes))
        }

        SettingsGroup(stringResource(R.string.settings_about)) {
            ValueRow(stringResource(R.string.app_name), stringResource(R.string.version, state.versionName))
            Text(
                stringResource(R.string.licences),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { showLicences = true }
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showLicences) {
        AlertDialog(
            onDismissRequest = { showLicences = false },
            title = { Text(stringResource(R.string.licences)) },
            text = { Text(stringResource(R.string.licences_body)) },
            confirmButton = { TextButton(onClick = { showLicences = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(
        title.uppercaseForUi(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
    )
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) { Column(content = content) }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
