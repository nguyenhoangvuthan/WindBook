package dev.kent.windbook.core.data.folders

import android.provider.DocumentsContract
import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.BookSourceEntity
import dev.kent.windbook.core.data.db.FolderEntity
import dev.kent.windbook.core.data.db.FolderMembershipEntity
import dev.kent.windbook.core.data.db.ScanRunEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ScanOptions
import dev.kent.windbook.core.model.SourceAvailability
import dev.kent.windbook.core.model.candidateFormat
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

data class FolderSummary(
    val id: String,
    val displayLocation: String,
    val options: ScanOptions,
    val connected: Boolean,
    val lastScanAt: Long?,
)

data class FolderEntry(
    val bookId: String,
    val sourceId: String,
    val name: String,
    val relativePath: String,
    val format: BookFormat,
    val availability: SourceAvailability,
    val title: String = name,
    val hidden: Boolean = false,
)

/** All scan writes and connection/grant changes share one lifecycle lock. */
class FolderRepository(
    private val db: WindBookDatabase,
    private val persistedReadTrees: () -> Set<String>,
    private val releaseGrant: suspend (String) -> Unit,
    private val acquireRead: suspend (String, Int) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutations = Mutex()
    private var initialized = false

    suspend fun initialize() = mutations.withLock { initializeLocked() }

    private suspend fun initializeLocked() {
        if (initialized) return
        db.withWriteTransaction { db.folderDao().interruptRunning() }
        sweepGrants()
        initialized = true
    }

    /** Picker grant acquisition and connection are serialized with every cleanup sweep. */
    suspend fun connect(
        treeUri: String,
        displayLocation: String,
        options: ScanOptions,
        returnedFlags: Int,
    ): String = mutations.withLock {
        requireTreeUri(treeUri)
        // Clean up stale disconnected grants before acquiring the permission from this picker result.
        initializeLocked()
        acquireRead(treeUri, returnedFlags)
        saveLocked(treeUri, displayLocation, options, reconnect = true)
    }

    /** Update remembered options; reconnecting a disconnected row requires [connect]. */
    suspend fun save(treeUri: String, displayLocation: String, options: ScanOptions): String = mutations.withLock {
        requireTreeUri(treeUri)
        initializeLocked()
        saveLocked(treeUri, displayLocation, options, reconnect = false)
    }

    private suspend fun saveLocked(
        treeUri: String,
        displayLocation: String,
        options: ScanOptions,
        reconnect: Boolean,
    ): String = db.withWriteTransaction {
        val old = db.folderDao().findByTree(treeUri)
        check(old?.disconnectedAt == null || reconnect) { "Use connect to reacquire a disconnected folder grant" }
        val folder = FolderEntity(old?.id ?: UUID.randomUUID().toString(), treeUri,
            displayLocation, encodeExtensions(options), options.recursive, lastScanAt = old?.lastScanAt)
        db.folderDao().upsert(folder)
        folder.id
    }

    fun observeFolders(): Flow<List<FolderSummary>> = db.folderDao().observeFolders().onStart { initialize() }
        .map { folders -> folders.map { FolderSummary(it.id, it.displayLocation, options(it),
            it.disconnectedAt == null, it.lastScanAt) } }

    fun observeEntries(folderId: String, query: String, formats: Set<BookFormat>, includeHidden: Boolean = false): Flow<List<FolderEntry>> =
        db.folderDao().observeEntries(folderId, includeHidden).onStart { initialize() }.map { rows ->
            rows.map { row -> FolderEntry(row.bookId, row.sourceId, row.relativePath.substringAfterLast('/'),
                row.relativePath, BookFormat.valueOf(row.format), SourceAvailability.valueOf(row.availability), row.title, row.deletedAt != null) }
                .filter { (formats.isEmpty() || it.format in formats) &&
                    (query.isBlank() || (it.relativePath.contains(query.trim(), ignoreCase = true) || it.title.contains(query.trim(), ignoreCase = true))) }
        }

    suspend fun disconnectFolder(folderId: String) = mutations.withLock {
        initializeLocked()
        db.withWriteTransaction {
            db.folderDao().interruptFolder(folderId)
            db.folderDao().disconnect(folderId, clock())
            db.sourceDao().disconnectOrphans(folderId)
        }
        sweepGrants()
    }

    internal suspend fun beginScan(folderId: String): Pair<FolderEntity, ScanRunEntity> = mutations.withLock {
        initializeLocked()
        db.withWriteTransaction {
            val folder = requireNotNull(db.folderDao().find(folderId)) { "Unknown folder" }
            check(folder.disconnectedAt == null) { "Folder is disconnected" }
            // Invalidate every prior token before issuing a new one, even if a collector lingers.
            db.folderDao().interruptRunning()
            val run = ScanRunEntity(UUID.randomUUID().toString(), folderId,
                encodeOptions(options(folder)), "RUNNING", startedAt = clock())
            db.folderDao().upsertRun(run)
            folder to run
        }
    }

    /** Discovery counters are cumulative; added/known are derived inside the same bounded transaction. */
    internal suspend fun writeBatch(
        runId: String,
        entries: List<ScanEvent.Found>,
        examined: Long,
        unreadable: Long,
        directoryErrors: Long,
        currentPath: String,
    ): ScanRunEntity? = mutations.withLock {
        require(entries.size <= 100)
        initializeLocked()
        val heldTrees = persistedReadTrees()
        db.withWriteTransaction {
            val run = db.folderDao().run(runId)?.takeIf { it.state == "RUNNING" }
                ?: return@withWriteTransaction null
            var added = run.added
            var known = run.known
            for (entry in entries) {
                val document = entry.document
                var source = db.sourceDao().findDocument(document.key.authority, document.key.documentId)
                if (source == null) {
                    val bookId = UUID.randomUUID().toString()
                    val format = requireNotNull(candidateFormat(document.name))
                    db.libraryDao().insert(BookEntity(
                        id = bookId, title = document.name, author = null, format = format.name,
                        filePath = null, fileSize = document.size ?: 0, sha256 = null,
                        coverPath = null, sourceUri = document.uri, status = "TO_READ",
                        addedAt = clock(), lastOpenedAt = null, updatedAt = clock(), deletedAt = null,
                    ))
                    source = BookSourceEntity(UUID.randomUUID().toString(), bookId, "EXTERNAL", uri = document.uri,
                        authority = document.key.authority, documentId = document.key.documentId,
                        size = document.size, mtime = document.modifiedAt)
                    db.sourceDao().insert(source)
                    added++
                } else {
                    // Retain usable canonical grants; repair revoked tree access only with the
                    // provider's discovered URI. Identity, memberships and reading data stay stable.
                    val oldUri = android.net.Uri.parse(requireNotNull(source.uri))
                    val oldTree = if (DocumentsContract.isTreeUri(oldUri))
                        DocumentsContract.buildTreeDocumentUri(oldUri.authority,
                            DocumentsContract.getTreeDocumentId(oldUri)).toString() else null
                    val repairAccess = oldTree != null && oldTree !in heldTrees
                    source = source.copy(uri = if (repairAccess) document.uri else source.uri,
                        size = document.size, mtime = document.modifiedAt,
                        availability = if (source.availability in setOf("MISSING", "DISCONNECTED", "ACCESS_DENIED"))
                            "UNVERIFIED" else source.availability)
                    db.sourceDao().update(source)
                    known++
                }
                db.folderDao().upsertMembership(FolderMembershipEntity(run.folderId, source.id,
                    entry.relativePath, entry.parentKey.documentId, runId, present = true))
            }
            run.copy(examined = examined, matched = run.matched + entries.size, added = added, known = known,
                unreadable = unreadable, directoryErrors = directoryErrors, currentPath = currentPath)
                .also { db.folderDao().upsertRun(it) }
        }
    }

    internal suspend fun finishScan(runId: String, state: String): ScanRunEntity? = mutations.withLock {
        require(state in setOf("COMPLETE", "INCOMPLETE", "INTERRUPTED"))
        initializeLocked()
        val result = db.withWriteTransaction {
            val run = db.folderDao().run(runId) ?: return@withWriteTransaction null
            if (run.state != "RUNNING") return@withWriteTransaction run
            // Counters also guard against an erroneous optimistic Finished event.
            val finalState = if (state == "COMPLETE" && (run.unreadable > 0 || run.directoryErrors > 0))
                "INCOMPLETE" else state
            if (finalState == "COMPLETE") reconcileMissing(run)
            db.folderDao().scanned(run.folderId, clock())
            run.copy(state = finalState).also { db.folderDao().upsertRun(it) }
        }
        sweepGrants()
        result
    }

    internal fun observeRun(folderId: String): Flow<ScanRunEntity?> =
        db.folderDao().observeLatestRun(folderId).onStart { initialize() }

    private suspend fun reconcileMissing(run: ScanRunEntity) {
        val options = decodeOptions(run.optionsJson)
        val folder = requireNotNull(db.folderDao().find(run.folderId))
        val rootId = DocumentsContract.getTreeDocumentId(requireTreeUri(folder.treeUri))
        for (member in db.folderDao().folderMemberships(run.folderId)) {
            if (member.lastSeenRun == run.id || !member.present) continue
            val format = candidateFormat(member.relativePath.substringAfterLast('/'))
            if (format !in options.extensions || (!options.recursive && member.parentDocumentId != rootId)) continue
            db.folderDao().markAbsent(run.folderId, member.sourceId)
            db.sourceDao().markMissingIfAbsent(member.sourceId)
        }
    }

    private suspend fun sweepGrants() {
        val held = persistedReadTrees()
        for (tree in db.folderDao().disconnectedTrees()) {
            if (tree in held && !db.sourceDao().connectedSourceUsesGrant(tree)) releaseGrant(tree)
        }
    }

    internal fun options(folder: FolderEntity): ScanOptions = ScanOptions(
        decodeExtensions(folder.extensionsJson), folder.recursive)

    private fun encodeExtensions(options: ScanOptions): String =
        JSONArray(options.extensions.map { it.name }.sorted()).toString()

    private fun decodeExtensions(json: String): Set<BookFormat> {
        val values = JSONArray(json)
        return (0 until values.length()).mapNotNull { i ->
            BookFormat.entries.firstOrNull { it.name == values.optString(i) }
        }.toSet()
    }

    private fun encodeOptions(options: ScanOptions): String = JSONObject()
        .put("extensions", JSONArray(options.extensions.map { it.name }.sorted()))
        .put("recursive", options.recursive).toString()

    private fun decodeOptions(json: String): ScanOptions {
        val value = JSONObject(json)
        return ScanOptions(decodeExtensions(value.getJSONArray("extensions").toString()), value.getBoolean("recursive"))
    }
}
