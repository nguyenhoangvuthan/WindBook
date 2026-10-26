package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update

@Dao
interface SourceDao {
    @Insert(onConflict = androidx.room3.OnConflictStrategy.REPLACE)
    suspend fun upsertTextProgress(state: TextReadingStateEntity)
    @Query("SELECT * FROM text_reading_states WHERE book_id = :bookId AND encoding = :encoding")
    suspend fun textProgress(bookId: String, encoding: String): TextReadingStateEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM text_reading_states WHERE book_id = :bookId)")
    suspend fun hasTextProgress(bookId: String): Boolean
    @Query("UPDATE books SET active_text_encoding = :encoding WHERE id = :bookId")
    suspend fun activateTextEncoding(bookId: String, encoding: String)
    @Query("DELETE FROM reading_progress WHERE book_id = :bookId")
    suspend fun clearProgress(bookId: String)

    @Query("""
        SELECT s.*, b.format AS bookFormat, b.sha256 AS bookSha256,
            b.cover_path AS coverPath, m.relative_path AS relativePath
        FROM book_sources s JOIN books b ON b.id = s.book_id
        JOIN folder_memberships m ON m.source_id = s.id
        JOIN folders f ON f.id = m.folder_id
        JOIN scan_runs r ON r.folder_id = f.id AND r.id = :runId
        WHERE r.state = 'RUNNING' AND m.last_seen_run = r.id AND m.present = 1
            AND f.disconnected_at IS NULL AND b.deleted_at IS NULL
            AND s.kind = 'EXTERNAL' AND b.format IN ('EPUB', 'PDF')
            AND s.id > :afterSourceId ORDER BY s.id LIMIT :limit
    """)
    suspend fun folderCoverCandidates(runId: String, afterSourceId: String, limit: Int): List<FolderCoverRow>

    @Query("""
        SELECT s.*, b.format AS bookFormat, b.sha256 AS bookSha256,
            b.cover_path AS coverPath, m.relative_path AS relativePath
        FROM book_sources s JOIN books b ON b.id = s.book_id
        JOIN folder_memberships m ON m.source_id = s.id
        JOIN folders f ON f.id = m.folder_id
        JOIN scan_runs r ON r.folder_id = f.id AND r.id = :runId
        WHERE r.state = 'RUNNING' AND m.last_seen_run = r.id AND m.present = 1
            AND f.disconnected_at IS NULL AND b.deleted_at IS NULL
            AND s.kind = 'EXTERNAL' AND b.format IN ('EPUB', 'PDF')
            AND s.id = :sourceId
    """)
    suspend fun folderCoverCandidate(runId: String, sourceId: String): FolderCoverRow?

    @Query("SELECT EXISTS(SELECT 1 FROM books WHERE cover_path = :path)")
    suspend fun coverIsReferenced(path: String): Boolean

    @Insert
    suspend fun insert(source: BookSourceEntity)

    @Update
    suspend fun update(source: BookSourceEntity)

    @Query("SELECT * FROM book_sources WHERE authority = :authority AND document_id = :documentId")
    suspend fun findDocument(authority: String, documentId: String): BookSourceEntity?

    @Query("""
        UPDATE book_sources SET availability = 'MISSING'
        WHERE id = :sourceId AND kind = 'EXTERNAL'
          AND NOT EXISTS (
            SELECT 1 FROM folder_memberships m JOIN folders f ON f.id = m.folder_id
            WHERE m.source_id = :sourceId AND m.present = 1 AND f.disconnected_at IS NULL
          )
    """)
    suspend fun markMissingIfAbsent(sourceId: String)

    @Query("SELECT * FROM book_sources WHERE book_id = :bookId ORDER BY CASE kind WHEN 'OWNED' THEN 0 ELSE 1 END, id")
    suspend fun forBook(bookId: String): List<BookSourceEntity>

    @Query("SELECT COUNT(*) FROM book_sources WHERE kind = 'OWNED'")
    suspend fun countOwned(): Int

    /** A document stays usable while any connected root still contains it. */
    @Query("""
        UPDATE book_sources SET availability = 'DISCONNECTED'
        WHERE kind = 'EXTERNAL'
          AND id IN (SELECT source_id FROM folder_memberships WHERE folder_id = :folderId)
          AND NOT EXISTS (
            SELECT 1 FROM folder_memberships m JOIN folders f ON f.id = m.folder_id
            WHERE m.source_id = book_sources.id AND f.disconnected_at IS NULL
          )
    """)
    suspend fun disconnectOrphans(folderId: String)

    /** Keep the canonical URI grant while any connected membership could use it again. */
    @Query("""
        SELECT EXISTS (
          SELECT 1 FROM book_sources s
          JOIN folder_memberships m ON m.source_id = s.id
          JOIN folders f ON f.id = m.folder_id
          WHERE f.disconnected_at IS NULL
            AND (s.uri = :treeUri OR substr(s.uri, 1, length(:treeUri) + 1) = :treeUri || '/')
        )
    """)
    suspend fun connectedSourceUsesGrant(treeUri: String): Boolean
}

data class FolderCoverRow(
    @androidx.room3.Embedded val source: BookSourceEntity,
    val bookFormat: String,
    val bookSha256: String?,
    val coverPath: String?,
    val relativePath: String,
) {
    fun candidate(runId: String) = dev.kent.windbook.core.data.folders.FolderCoverCandidate(
        runId, source, dev.kent.windbook.core.model.BookFormat.valueOf(bookFormat), bookSha256, coverPath, relativePath)
}
