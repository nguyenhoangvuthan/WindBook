package dev.kent.windbook.folders

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kent.windbook.R
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.data.folders.FolderScanCoordinator
import dev.kent.windbook.core.model.ScanOptions
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class FolderViewModel @Inject constructor(
    private val repository: FolderRepository,
    private val scans: FolderScanCoordinator,
    private val db: WindBookDatabase,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val mutable = MutableStateFlow(FolderUiState())
    val state = mutable.asStateFlow()
    private val commands = Mutex()
    private var entriesJob: Job? = null
    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeFolders().collect { folders ->
                mutable.update { current -> current.copy(folders = folders,
                    connected = folders.firstOrNull { it.id == current.resolvedFolderId }?.connected ?: false) }
            }
        }
    }

    fun onLocationEdited(value: String) {
        entriesJob?.cancel()
        progressJob?.cancel()
        mutable.update { it.copy(locationHint = value, resolvedFolderId = null, selectedLocation = "",
            connected = false, entries = emptyList()) }
    }

    fun selectFolder(id: String) = command {
        val folder = db.folderDao().find(id) ?: return@command
        val summary = repository.observeFolders().first().firstOrNull { it.id == id } ?: return@command
        select(id, folder.displayLocation, summary.options, folder.disconnectedAt == null)
    }

    fun onTreeGranted(uri: Uri, flags: Int) = command {
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        // Connect first: metadata must be read under the permission actually returned by Android.
        val id = repository.connect(uri.toString(), documentId, state.value.options, flags)
        val location = if (uri.authority == "com.android.externalstorage.documents") {
            val volume = documentId.substringBefore(':')
            val root = if (volume == "primary") context.getString(R.string.folder_internal_storage) else volume
            root + documentId.substringAfter(':', "").takeIf { it.isNotEmpty() }?.let { " / $it" }.orEmpty()
        } else withContext(Dispatchers.IO) {
            try {
                context.contentResolver.query(DocumentsContract.buildDocumentUriUsingTree(uri, documentId),
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: documentId
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { documentId }
        }
        repository.save(uri.toString(), location, state.value.options)
        select(id, location, state.value.options, true)
    }

    private fun select(id: String, location: String, options: ScanOptions, connected: Boolean) {
        mutable.update { it.copy(resolvedFolderId = id, locationHint = location, selectedLocation = location,
            connected = connected, options = options, message = null) }
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            repository.observeEntries(id, "", emptySet(), includeHidden = true).collect { rows ->
                mutable.update { it.copy(entries = rows) }
            }
        }
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            scans.observe(id).collect { progress -> mutable.update { it.copy(progress = progress) } }
        }
    }

    fun setOptions(options: ScanOptions) = command {
        mutable.update { it.copy(options = options) }
        val id = state.value.resolvedFolderId ?: return@command
        val folder = db.folderDao().find(id) ?: return@command
        if (folder.disconnectedAt != null) return@command
        repository.save(folder.treeUri, folder.displayLocation, options)
        val progress = scans.observe(id).first()
        if (progress.state == "RUNNING") {
            if (options.extensions.isEmpty()) scans.stop(progress.runId) else scans.start(id)
        }
    }

    fun scan() = command {
        if (state.value.connected && state.value.options.extensions.isNotEmpty())
            state.value.resolvedFolderId?.let { scans.start(it) }
    }
    fun stop() = command { scans.stop(state.value.progress.runId) }
    fun setQuery(query: String) { mutable.update { it.copy(query = query) } }
    fun showHidden(value: Boolean) { mutable.update { it.copy(showHidden = value) } }
    fun restore(bookId: String) = command {
        // Explicitly restore only a hidden external record belonging to this selected folder.
        if (state.value.entries.any { it.bookId == bookId && it.hidden })
            db.libraryDao().restoreExternal(bookId, System.currentTimeMillis())
    }
    fun disconnect() = command {
        state.value.resolvedFolderId?.let { id ->
            val progress = scans.observe(id).first()
            if (progress.state == "RUNNING") scans.stop(progress.runId)
            repository.disconnectFolder(id)
        }
    }

    private fun command(action: suspend () -> Unit) = viewModelScope.launch {
        commands.withLock {
            mutable.update { it.copy(busy = true, message = null) }
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(message = R.string.folder_error) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
}
