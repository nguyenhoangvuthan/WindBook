package dev.kent.windbook.core.data.folders

import android.provider.DocumentsContract
import dev.kent.windbook.core.data.db.ScanRunEntity
import dev.kent.windbook.core.model.DocumentKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ScanProgress(
    val runId: String,
    val state: String,
    val examined: Long = 0,
    val matched: Long = 0,
    val added: Long = 0,
    val known: Long = 0,
    val unreadable: Long = 0,
    val directoryErrors: Long = 0,
    val currentPath: String = "",
)

/** Bound as one app singleton; scans remain owned by the app when a screen closes. */
class FolderScanCoordinator(
    private val repository: FolderRepository,
    private val scanner: FolderScanner,
    private val scope: CoroutineScope,
) {
    private val controls = Mutex()
    private var active: Pair<String, Job>? = null

    suspend fun start(folderId: String): String = controls.withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            stopActive()
            val (folder, run) = repository.beginScan(folderId)
            val tree = requireTreeUri(folder.treeUri)
            val root = DocumentKey(requireNotNull(tree.authority), DocumentsContract.getTreeDocumentId(tree))
            active = run.id to scope.launch {
                val batch = ArrayList<ScanEvent.Found>(100)
                var examined = 0L
                var unreadable = 0L
                var errors = 0L
                var path = ""
                var complete = false
                suspend fun flush() {
                    currentCoroutineContext().ensureActive()
                    if (repository.writeBatch(run.id, batch, examined, unreadable, errors, path) == null)
                        throw CancellationException("Scan token expired")
                    batch.clear()
                }
                try {
                    scanner.scan(folder.treeUri, root, repository.options(folder)).collect { event ->
                        currentCoroutineContext().ensureActive()
                        when (event) {
                            is ScanEvent.Found -> {
                                batch.add(event)
                                path = event.relativePath
                                if (batch.size == 100) flush()
                            }
                            is ScanEvent.Examined -> examined = event.count
                            is ScanEvent.Unreadable -> unreadable++
                            is ScanEvent.DirectoryFailed -> errors++
                            is ScanEvent.Finished -> complete = event.complete
                        }
                        // Bound progress latency even for trees containing no selected files.
                        if (event is ScanEvent.Examined && examined % 100 == 0L) flush()
                    }
                    flush()
                    repository.finishScan(run.id, if (complete) "COMPLETE" else "INCOMPLETE")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Persist delivered entries and a conservative error before closing this token.
                    errors++
                    flush()
                    repository.finishScan(run.id, "INCOMPLETE")
                } finally {
                    withContext(NonCancellable) { repository.finishScan(run.id, "INTERRUPTED") }
                }
            }
            run.id
        }
    }

    suspend fun stop(runId: String) = controls.withLock {
        withContext(NonCancellable) {
            // Invalidate durably before cancelling: even non-cooperative late writers cannot commit.
            repository.finishScan(runId, "INTERRUPTED")
            if (active?.first == runId) {
                active?.second?.cancelAndJoin()
                active = null
            }
        }
    }

    private suspend fun stopActive() {
        val previous = active ?: return
        repository.finishScan(previous.first, "INTERRUPTED")
        previous.second.cancelAndJoin()
        active = null
    }

    fun observe(folderId: String): Flow<ScanProgress> = repository.observeRun(folderId).map { run ->
        run?.progress() ?: ScanProgress("", "IDLE")
    }
}

private fun ScanRunEntity.progress() = ScanProgress(id, state, examined, matched, added, known,
    unreadable, directoryErrors, currentPath)
