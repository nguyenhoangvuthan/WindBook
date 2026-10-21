package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert

@Dao
interface FolderDao {
    @Upsert
    suspend fun upsert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun find(id: String): FolderEntity?

    @Query("UPDATE folders SET disconnected_at = :now WHERE id = :id")
    suspend fun disconnect(id: String, now: Long)

    @Upsert
    suspend fun upsertMembership(membership: FolderMembershipEntity)

    @Query("SELECT * FROM folder_memberships WHERE source_id = :sourceId ORDER BY folder_id")
    suspend fun memberships(sourceId: String): List<FolderMembershipEntity>

    @Upsert
    suspend fun upsertRun(run: ScanRunEntity)

    @Query("SELECT * FROM scan_runs WHERE id = :id")
    suspend fun run(id: String): ScanRunEntity?
}
