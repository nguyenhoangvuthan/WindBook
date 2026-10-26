package dev.kent.windbook.folders

import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.folders.FolderBookEnricher
import dev.kent.windbook.core.data.folders.FolderCoverCandidate
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceLease
import dev.kent.windbook.reader.source.SourceMetadata
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** One bounded snapshot and one cover at a time; no reading session or progress writes. */
class FolderCoverLoader(
    private val books: BookSourcesRepository,
    private val access: SourceAccess,
    private val metadata: SourceMetadata,
    private val files: BookFileStore,
) : FolderBookEnricher {
    override suspend fun enrich(runId: String) = withContext(Dispatchers.IO) {
        var after = ""
        while (true) {
            currentCoroutineContext().ensureActive()
            val batch = books.folderCoverCandidates(runId, after)
            if (batch.isEmpty()) break
            for (candidate in batch) {
                currentCoroutineContext().ensureActive()
                prepare(candidate)
            }
            after = batch.last().source.id
        }
    }

    private suspend fun prepare(candidate: FolderCoverCandidate) {
        val row = candidate.source
        if (row.availability == "AVAILABLE" && row.size != null && (row.mtime ?: 0) > 0 &&
            row.verifiedSha256 != null && row.verifiedSha256 == candidate.bookSha256 &&
            candidate.coverPath?.let { File(it).isFile } == true) return
        var lease: SourceLease? = null
        var staged: File? = null
        var accepted = false
        var superseded: String? = null
        try {
            val source = BookSource.External(requireNotNull(row.uri),
                DocumentKey(requireNotNull(row.authority), requireNotNull(row.documentId)))
            val before = access.metadata(source)
            val snapshot = access.acquire(source).also { lease = it }
            val sha = snapshot.fingerprint()
            val info = metadata.read(snapshot, candidate.format).getOrElse {
                if (it is CancellationException) throw it
                return
            }
            if (before.changed(access.metadata(source))) return
            val owner = currentCoroutineContext()
            owner.ensureActive()
            info.coverWebp?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val id = "folder-${UUID.randomUUID()}"
                // Record ownership before writing so even a partial failed write is cleaned up.
                staged = File(files.coversDir, "$id.webp")
                files.writeCover(id, bytes)
            }
            // A committed path owns its file even if cancellation races database delivery.
            withContext(NonCancellable) {
                owner.ensureActive()
                val published = books.commitFolderMetadata(candidate, sha, info, staged?.path)
                accepted = published.accepted
                superseded = published.supersededCoverPath
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Covers are optional: an unavailable/corrupt book must not block later candidates.
        } finally {
            withContext(NonCancellable) {
                try { lease?.close() } finally {
                    if (!accepted) staged?.delete()
                    else superseded?.let { path ->
                        // These unique paths are never reused by readers or future publications.
                        // Legacy reader paths are retained; only this loader owns folder-* files.
                        val retired = File(path)
                        try {
                            if (retired.name.startsWith("folder-") && retired.name.endsWith(".webp") &&
                                retired.parentFile?.canonicalFile == files.coversDir.canonicalFile &&
                                !books.coverIsReferenced(path)) retired.delete()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Best-effort cleanup must not discard the new cover. */ }
                    }
                }
            }
        }
    }
}
