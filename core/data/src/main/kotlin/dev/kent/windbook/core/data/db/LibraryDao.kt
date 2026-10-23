package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Update
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {

    @Query(
        """
        SELECT b.*, COALESCE(p.progression, 0.0) AS progression
        FROM books b LEFT JOIN reading_progress p ON p.book_id = b.id
        WHERE b.deleted_at IS NULL
        """,
    )
    fun observeLibrary(): Flow<List<BookWithProgressRow>>

    @Query("SELECT * FROM books WHERE id = :id AND deleted_at IS NULL")
    suspend fun findById(id: String): BookEntity?

    /** Includes tombstoned rows so a re-import can revive them. */
    @Query("SELECT * FROM books WHERE sha256 = :sha256 ORDER BY CASE WHEN deleted_at IS NULL THEN 0 ELSE 1 END, CASE WHEN file_path IS NOT NULL THEN 0 ELSE 1 END, added_at, id LIMIT 1")
    suspend fun findBySha(sha256: String): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun findIncludingHidden(id: String): BookEntity?

    @Query("SELECT id FROM books")
    suspend fun allIdsIncludingHidden(): List<String>

    /** Explicit Restore after matching verified bytes also revives an imported tombstone. */
    @Query("UPDATE books SET deleted_at = NULL, updated_at = :now WHERE id = :id")
    suspend fun restoreVerified(id: String, now: Long)

    @Query("UPDATE books SET deleted_at = NULL, updated_at = :now WHERE id = :id AND file_path IS NULL")
    suspend fun restoreExternal(id: String, now: Long)

    @Query("UPDATE books SET deleted_at = :now, updated_at = :now WHERE id = :id AND file_path IS NULL")
    suspend fun hideExternal(id: String, now: Long)

    @Upsert
    suspend fun upsertOwnedSource(source: BookSourceEntity)

    /** Both records commit together; source failures must never leave a book without its source. */
    @Transaction
    suspend fun insertOwned(book: BookEntity) {
        insert(book)
        upsertOwnedSource(book.ownedSource())
    }

    /** Nonunique revision hashes still require atomic canonical selection for owned imports. */
    @Transaction
    suspend fun insertOwnedUnlessDuplicate(book: BookEntity): String? {
        val winner = findBySha(requireNotNull(book.sha256))
        if (winner != null) return winner.id
        insertOwned(book)
        return null
    }

    @Transaction
    suspend fun updateOwned(book: BookEntity) {
        update(book)
        upsertOwnedSource(book.ownedSource())
    }

    @Insert
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET status = :status, updated_at = :now WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, now: Long)

    @Query(
        """
        UPDATE books SET last_opened_at = :now, updated_at = :now,
            status = CASE WHEN status = 'TO_READ' THEN 'READING' ELSE status END
        WHERE id = :id
        """,
    )
    suspend fun markOpened(id: String, now: Long)

    @Query("UPDATE books SET deleted_at = :now, updated_at = :now WHERE id = :id")
    suspend fun tombstone(id: String, now: Long)

    @Query("DELETE FROM reading_progress WHERE book_id = :id")
    suspend fun deleteProgress(id: String)

    @Transaction
    suspend fun softDelete(id: String, now: Long) {
        tombstone(id, now)
        deleteProgress(id)
    }

    /** Hard delete; used by tombstone garbage collection after sync (M7) and by tests. */
    @Query("DELETE FROM books WHERE id = :id")
    suspend fun purge(id: String)

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE book_id = :bookId")
    suspend fun progress(bookId: String): ReadingProgressEntity?
}

private fun BookEntity.ownedSource() = BookSourceEntity(
    id = "owned:$id",
    bookId = id,
    kind = "OWNED",
    localPath = requireNotNull(filePath),
    size = fileSize,
    verifiedSha256 = requireNotNull(sha256),
    availability = "AVAILABLE",
)
