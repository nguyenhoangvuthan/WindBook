package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun find(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE tree_uri = :treeUri")
    suspend fun findByTree(treeUri: String): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY display_location, id")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT tree_uri FROM folders WHERE disconnected_at IS NOT NULL ORDER BY id")
    suspend fun disconnectedTrees(): List<String>

    @Query("UPDATE folders SET disconnected_at = :now WHERE id = :id")
    suspend fun disconnect(id: String, now: Long)

    @Query("UPDATE folders SET last_scan_at = :now WHERE id = :id")
    suspend fun scanned(id: String, now: Long)

    @Upsert
    suspend fun upsertMembership(membership: FolderMembershipEntity)

    @Query("SELECT * FROM folder_memberships WHERE source_id = :sourceId ORDER BY folder_id")
    suspend fun memberships(sourceId: String): List<FolderMembershipEntity>

    @Query("SELECT * FROM folder_memberships WHERE folder_id = :folderId")
    suspend fun folderMemberships(folderId: String): List<FolderMembershipEntity>

    @Query("UPDATE folder_memberships SET present = 0 WHERE folder_id = :folderId AND source_id = :sourceId")
    suspend fun markAbsent(folderId: String, sourceId: String)

    @Query("""
        SELECT b.id AS bookId, s.id AS sourceId, m.relative_path AS relativePath,
          b.format, s.availability, b.title, b.deleted_at AS deletedAt, b.added_at AS addedAt
        FROM folder_memberships m JOIN book_sources s ON s.id = m.source_id
        JOIN books b ON b.id = s.book_id
        WHERE m.folder_id = :folderId AND (:includeHidden OR b.deleted_at IS NULL)
        ORDER BY m.relative_path COLLATE NOCASE, s.id
    """)
    fun observeEntries(folderId: String, includeHidden: Boolean = false): Flow<List<FolderEntryRow>>

    @Upsert
    suspend fun upsertRun(run: ScanRunEntity)

    @Query("SELECT * FROM scan_runs WHERE id = :id")
    suspend fun run(id: String): ScanRunEntity?

    // Upsert updates retain rowid. It breaks equal-clock ties by insertion order, not random UUID.
    @Query("SELECT * FROM scan_runs WHERE folder_id = :folderId ORDER BY started_at DESC, rowid DESC LIMIT 1")
    fun observeLatestRun(folderId: String): Flow<ScanRunEntity?>

    @Query("UPDATE scan_runs SET state = 'INTERRUPTED' WHERE state = 'RUNNING'")
    suspend fun interruptRunning()

    @Query("UPDATE scan_runs SET state = 'INTERRUPTED' WHERE folder_id = :folderId AND state = 'RUNNING'")
    suspend fun interruptFolder(folderId: String)
}

data class FolderEntryRow(
    val bookId: String,
    val sourceId: String,
    val relativePath: String,
    val format: String,
    val availability: String,
    val title: String,
    val deletedAt: Long?,
    val addedAt: Long,
)
