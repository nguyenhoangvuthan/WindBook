package dev.kent.windbook.folders

import dev.kent.windbook.core.data.folders.FolderEntry
import dev.kent.windbook.core.data.folders.FolderSummary
import dev.kent.windbook.core.data.folders.ScanProgress
import dev.kent.windbook.core.model.ScanOptions

data class FolderUiState(
    val locationHint: String = "",
    val resolvedFolderId: String? = null,
    val selectedLocation: String = "",
    val connected: Boolean = false,
    val options: ScanOptions = ScanOptions(),
    val progress: ScanProgress = ScanProgress("", "IDLE"),
    val entries: List<FolderEntry> = emptyList(),
    val folders: List<FolderSummary> = emptyList(),
    val query: String = "",
    val message: Int? = null,
    val busy: Boolean = false,
    val showHidden: Boolean = false,
) {
    val canScan get() = connected && resolvedFolderId != null && options.extensions.isNotEmpty() && !busy
}

data class FolderActions(
    val onLocationEdited: (String) -> Unit = {},
    val setOptions: (ScanOptions) -> Unit = {},
    val scan: () -> Unit = {},
    val stop: () -> Unit = {},
    val setQuery: (String) -> Unit = {},
    val disconnect: () -> Unit = {},
    val pickFolder: () -> Unit = {},
    val selectFolder: (String) -> Unit = {},
    val openBook: (String) -> Unit = {},
    val restore: (String) -> Unit = {},
    val back: () -> Unit = {},
    val showHidden: (Boolean) -> Unit = {},
)
