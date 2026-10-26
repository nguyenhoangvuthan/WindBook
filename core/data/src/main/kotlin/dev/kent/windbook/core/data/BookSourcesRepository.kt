package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.db.BookSourceEntity
import androidx.room3.withWriteTransaction
import java.util.UUID
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey

class BookSourcesRepository(
    private val db: WindBookDatabase,
    private val folders: FolderRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sources(bookId: String): List<BookSource> = db.sourceDao().forBook(bookId).map { source ->
        when (source.kind) {
            "OWNED" -> BookSource.Owned(requireNotNull(source.localPath))
            "EXTERNAL" -> BookSource.External(
                requireNotNull(source.uri),
                DocumentKey(requireNotNull(source.authority), requireNotNull(source.documentId)),
            )
            else -> error("Unknown source kind: ${source.kind}")
        }
    }

    private suspend fun sourceRow(bookId: String, source: BookSource) = db.sourceDao().forBook(bookId).firstOrNull { row ->
        when (source) {
            is BookSource.Owned -> row.kind == "OWNED" && row.localPath == source.path
            is BookSource.External -> row.authority == source.key.authority && row.documentId == source.key.documentId
        }
    }
    suspend fun textEncoding(bookId: String, source: BookSource): String? = sourceRow(bookId, source)?.txtEncodingOverride
    suspend fun setTextEncoding(bookId: String, source: BookSource, encoding: String) = db.withWriteTransaction {
        require(encoding in setOf("UTF8", "UTF16_LE", "UTF16_BE", "WINDOWS_1258"))
        val row = sourceRow(bookId, source)
        if (row != null) db.sourceDao().update(row.copy(txtEncodingOverride = encoding))
        else {
            require(source is BookSource.Owned)
            db.sourceDao().insert(BookSourceEntity(UUID.randomUUID().toString(), bookId, "OWNED", localPath = source.path, txtEncodingOverride = encoding))
        }
    }
    /** Restore this derived revision and atomically mirror it for Library/Reading queries. */
    suspend fun activateTextEncoding(bookId: String, encoding: String): String? = db.withWriteTransaction {
        val state = db.sourceDao().textProgress(bookId, encoding)
        db.sourceDao().activateTextEncoding(bookId, encoding)
        if (state == null) db.sourceDao().clearProgress(bookId)
        else db.libraryDao().upsertProgress(dev.kent.windbook.core.data.db.ReadingProgressEntity(bookId, state.locatorJson, state.progression, state.updatedAt))
        state?.locatorJson
    }
    suspend fun saveTextProgress(bookId: String, encoding: String, locator: String, progression: Double) = db.withWriteTransaction {
        val now = clock()
        db.sourceDao().upsertTextProgress(dev.kent.windbook.core.data.db.TextReadingStateEntity(bookId, encoding, locator, progression, now))
        if (db.libraryDao().findIncludingHidden(bookId)?.activeTextEncoding == encoding) {
            db.libraryDao().upsertProgress(dev.kent.windbook.core.data.db.ReadingProgressEntity(bookId, locator, progression, now))
        }
    }

    /** Identity decisions and source transfers commit together; no reading row is removed. */
    suspend fun verify(bookId: String, source: BookSource, sha256: String): Verification =
        db.withWriteTransaction { verifyLocked(bookId, source, sha256) }

    suspend fun reconnect(bookId: String, replacement: BookSource, sha256: String): Verification =
        db.withWriteTransaction { verifyLocked(bookId, replacement, sha256) }

    private suspend fun verifyLocked(bookId: String, source: BookSource, sha: String, allowRedirect: Boolean = true): Verification {
        val books = db.libraryDao()
        val book = requireNotNull(books.findIncludingHidden(bookId))
        if (book.deletedAt != null) return Verification.HiddenMatch(bookId)
        val existing = when (source) {
            is BookSource.External -> db.sourceDao().findDocument(source.key.authority, source.key.documentId)
            is BookSource.Owned -> db.sourceDao().forBook(bookId).firstOrNull { it.localPath == source.path }
        }
        val match = books.findBySha(sha)?.takeIf { it.id != bookId }
        val hasData = db.annotationDao().hasAnnotations(bookId) || db.sourceDao().hasTextProgress(bookId) || books.progress(bookId) != null || db.bookPreferencesDao().json(bookId) != null ||
            book.lastOpenedAt != null || book.status != "TO_READ"
        val proposed = SourceRevisionPolicy.decide(book.sha256, sha, match != null, hasData, match?.deletedAt != null)
        // A background cover must not tombstone an ID already captured by a pending reader.
        // Foreground verification still performs the existing explicit redirect.
        val decision = if (!allowRedirect && proposed == RevisionDecision.REDIRECT) RevisionDecision.READY else proposed
        if (decision == RevisionDecision.HIDDEN) return Verification.HiddenMatch(requireNotNull(match).id)
        var target = bookId
        var result: Verification = Verification.Ready(bookId, source)
        when (decision) {
            RevisionDecision.CHANGED -> {
                // A known revision (including a read conflict) retains its durable book fingerprint.
                if (match?.deletedAt != null) return Verification.HiddenMatch(match.id)
                target = match?.id ?: UUID.randomUUID().toString().also { id ->
                    books.insert(book.copy(id = id, sha256 = sha, filePath = null, coverPath = null,
                        status = "TO_READ", lastOpenedAt = null, activeTextEncoding = null, addedAt = clock(), updatedAt = clock()))
                }
                result = Verification.Changed(target)
            }
            RevisionDecision.REDIRECT -> {
                target = requireNotNull(match).id
                for (oldSource in db.sourceDao().forBook(bookId)) db.sourceDao().update(oldSource.copy(bookId = target))
                books.tombstone(bookId, clock())
                result = Verification.Redirect(target)
            }
            RevisionDecision.READY -> books.update(book.copy(sha256 = sha, updatedAt = clock()))
            RevisionDecision.HIDDEN -> error("Handled above")
        }
        val row = existing ?: when (source) {
            is BookSource.Owned -> BookSourceEntity(UUID.randomUUID().toString(), target, "OWNED", localPath = source.path)
            is BookSource.External -> BookSourceEntity(UUID.randomUUID().toString(), target, "EXTERNAL", uri = source.uri,
                authority = source.key.authority, documentId = source.key.documentId)
        }
        val verified = row.copy(bookId = target, uri = (source as? BookSource.External)?.uri ?: row.uri,
            verifiedSha256 = sha, availability = "AVAILABLE")
        if (existing == null) db.sourceDao().insert(verified) else db.sourceDao().update(verified)
        return result
    }

    suspend fun folderCoverCandidates(runId: String, afterSourceId: String = "", limit: Int = 64): List<dev.kent.windbook.core.data.folders.FolderCoverCandidate> {
        require(limit in 1..100)
        return db.sourceDao().folderCoverCandidates(runId, afterSourceId, limit).map { it.candidate(runId) }
    }

    /** Stop/disconnect/revision changes and publication share the same database transaction. */
    suspend fun commitFolderMetadata(candidate: dev.kent.windbook.core.data.folders.FolderCoverCandidate,
        sha256: String, metadata: ExtractedMetadata, coverPath: String?): dev.kent.windbook.core.data.folders.FolderMetadataCommit = db.withWriteTransaction {
        val current = db.sourceDao().folderCoverCandidate(candidate.runId, candidate.source.id)
            ?.candidate(candidate.runId) ?: return@withWriteTransaction dev.kent.windbook.core.data.folders.FolderMetadataCommit(false)
        if (current != candidate) return@withWriteTransaction dev.kent.windbook.core.data.folders.FolderMetadataCommit(false)
        val source = BookSource.External(requireNotNull(current.source.uri),
            DocumentKey(requireNotNull(current.source.authority), requireNotNull(current.source.documentId)))
        val target = when (val verified = verifyLocked(current.source.bookId, source, sha256, allowRedirect = false)) {
            is Verification.Ready -> verified.bookId
            is Verification.Redirect -> verified.bookId
            is Verification.Changed -> verified.newBookId
            is Verification.HiddenMatch -> return@withWriteTransaction dev.kent.windbook.core.data.folders.FolderMetadataCommit(false)
        }
        val book = db.libraryDao().findById(target) ?: return@withWriteTransaction dev.kent.windbook.core.data.folders.FolderMetadataCommit(false)
        db.libraryDao().update(book.copy(title = metadata.title?.takeIf { it.isNotBlank() } ?: book.title,
            author = metadata.author ?: book.author, coverPath = coverPath ?: book.coverPath, updatedAt = clock()))
        dev.kent.windbook.core.data.folders.FolderMetadataCommit(true,
            book.coverPath?.takeIf { coverPath != null && it != coverPath })
    }

    /** Include hidden/revision rows: their images remain durable reading data. */
    suspend fun coverIsReferenced(path: String): Boolean = db.sourceDao().coverIsReferenced(path)

    suspend fun enrich(bookId: String, metadata: ExtractedMetadata, coverPath: String?) = db.withWriteTransaction {
        val book = db.libraryDao().findIncludingHidden(bookId) ?: return@withWriteTransaction
        db.libraryDao().update(book.copy(title = metadata.title?.takeIf { it.isNotBlank() } ?: book.title,
            author = metadata.author ?: book.author, coverPath = coverPath ?: book.coverPath, updatedAt = clock()))
    }

    suspend fun hideExternalBook(bookId: String) = db.libraryDao().hideExternal(bookId, clock())

    suspend fun restoreExternalBook(bookId: String) = db.libraryDao().restoreVerified(bookId, clock())

    suspend fun disconnectFolder(folderId: String) = folders.disconnectFolder(folderId)
}
