package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query

@Dao
interface SourceDao {
    @Insert
    suspend fun insert(source: BookSourceEntity)

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

    /** Membership overlap does not imply that a stored URI uses the other root's grant. */
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
