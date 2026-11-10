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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.update
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
    val preparingCovers: Boolean = false,
    val coversChecked: Long = 0,
    val coversDiscovered: Long = 0,
    val discoveryComplete: Boolean = false,
)

private data class CoverProgress(val runId: String, val checked: Long = 0, val discovered: Long = 0, val discoveryComplete: Boolean = false)

/** Bound as one app singleton; scans remain owned by the app when a screen closes. */
class FolderScanCoordinator(
    private val repository: FolderRepository,
    private val scanner: FolderScanner,
    private val scope: CoroutineScope,
    private val enricher: FolderBookEnricher = FolderBookEnricher { },
) {
    private val coverRun = MutableStateFlow<String?>(null)
    private val coverProgress = MutableStateFlow<CoverProgress?>(null)
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
                var deliveredFirst = false
                coverProgress.value = CoverProgress(run.id)
                suspend fun flush(queue: Channel<String>? = null) {
                    currentCoroutineContext().ensureActive()
                    val committed = repository.writeCommittedBatch(run.id, batch, examined, unreadable, errors, path)
                        ?: throw CancellationException("Scan token expired")
                    batch.clear()
                    if (queue != null) {
                        coverProgress.update { it?.copy(discovered = it.discovered + committed.coverSourceIds.size) }
                        for (id in committed.coverSourceIds) queue.send(id)
                    }
                }
                try {
                    coroutineScope {
                        // One consumer and a bounded identifier queue; no parallel snapshot/extractor.
                        val queue = Channel<String>(64)
                        val worker = launch {
                            for (id in queue) {
                                currentCoroutineContext().ensureActive()
                                coverRun.value = run.id
                                val ids = buildList {
                                    add(id)
                                    while (size < 16) add(queue.tryReceive().getOrNull() ?: break)
                                }
                                enricher.enrichBatch(run.id, ids)
                                coverProgress.update { it?.copy(checked = it.checked + ids.size) }
                            }
                        }
                        try {
                            scanner.scan(folder.treeUri, root, repository.options(folder)).collect { event ->
                                currentCoroutineContext().ensureActive()
                                when (event) {
                                    is ScanEvent.Found -> {
                                        batch.add(event)
                                        path = event.relativePath
                                        if (!deliveredFirst || batch.size == 100) {
                                            flush(queue)
                                            deliveredFirst = true
                                        }
                                    }
                                    is ScanEvent.Examined -> examined = event.count
                                    is ScanEvent.Unreadable -> unreadable++
                                    is ScanEvent.DirectoryFailed -> errors++
                                    is ScanEvent.Finished -> complete = event.complete
                                }
                                if (event is ScanEvent.Examined && examined % 100 == 0L) flush(queue)
                            }
                            flush(queue)
                            coverProgress.update { it?.copy(discoveryComplete = true) }
                        } finally { queue.close() }
                        worker.join()
                    }
                    currentCoroutineContext().ensureActive()
                    coverRun.value = run.id
                    // Discovery is now closed; a full keyset sweep safely catches changed/late rows.
                    enricher.enrich(run.id)
                    currentCoroutineContext().ensureActive()
                    coverRun.value = null
                    repository.finishScan(run.id, if (complete) "COMPLETE" else "INCOMPLETE")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Persist delivered entries and a conservative error before closing this token.
                    errors++
                    flush()
                    repository.finishScan(run.id, "INCOMPLETE")
                } finally {
                    if (coverRun.value == run.id) coverRun.value = null
                    if (coverProgress.value?.runId == run.id) coverProgress.value = null
                    withContext(NonCancellable) {
                        try { enricher.cleanup(run.id) }
                        finally { repository.finishScan(run.id, "INTERRUPTED") }
                    }
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

    fun observe(folderId: String): Flow<ScanProgress> = combine(repository.observeRun(folderId), coverRun, coverProgress) { run, covering, covers ->
        run?.progress()?.copy(
            preparingCovers = run.state == "RUNNING" && run.id == covering,
            coversChecked = covers?.takeIf { it.runId == run.id }?.checked ?: 0,
            coversDiscovered = covers?.takeIf { it.runId == run.id }?.discovered ?: 0,
            discoveryComplete = covers?.takeIf { it.runId == run.id }?.discoveryComplete ?: false,
        ) ?: ScanProgress("", "IDLE")
    }
}

private fun ScanRunEntity.progress() = ScanProgress(id, state, examined, matched, added, known,
    unreadable, directoryErrors, currentPath)
