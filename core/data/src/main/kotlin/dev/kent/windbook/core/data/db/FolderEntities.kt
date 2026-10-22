package dev.kent.windbook.core.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "folders", indices = [Index(value = ["tree_uri"], unique = true)])
data class FolderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "tree_uri") val treeUri: String,
    @ColumnInfo(name = "display_location") val displayLocation: String,
    @ColumnInfo(name = "extensions_json") val extensionsJson: String,
    val recursive: Boolean,
    @ColumnInfo(name = "disconnected_at") val disconnectedAt: Long? = null,
    @ColumnInfo(name = "last_scan_at") val lastScanAt: Long? = null,
)

@Entity(
    tableName = "book_sources",
    foreignKeys = [ForeignKey(entity = BookEntity::class, parentColumns = ["id"], childColumns = ["book_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("book_id"), Index(value = ["authority", "document_id"], unique = true)],
)
data class BookSourceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "book_id") val bookId: String,
    val kind: String,
    @ColumnInfo(name = "local_path") val localPath: String? = null,
    val uri: String? = null,
    val authority: String? = null,
    @ColumnInfo(name = "document_id") val documentId: String? = null,
    val size: Long? = null,
    val mtime: Long? = null,
    @ColumnInfo(name = "verified_sha256") val verifiedSha256: String? = null,
    val availability: String = "UNVERIFIED",
)

@Entity(
    tableName = "folder_memberships",
    primaryKeys = ["folder_id", "source_id"],
    foreignKeys = [
        ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folder_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = BookSourceEntity::class, parentColumns = ["id"], childColumns = ["source_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("source_id")],
)
data class FolderMembershipEntity(
    @ColumnInfo(name = "folder_id") val folderId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "parent_document_id") val parentDocumentId: String,
    @ColumnInfo(name = "last_seen_run") val lastSeenRun: String,
    @ColumnInfo(defaultValue = "1") val present: Boolean = true,
)

@Entity(
    tableName = "scan_runs",
    foreignKeys = [ForeignKey(entity = FolderEntity::class, parentColumns = ["id"], childColumns = ["folder_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("folder_id")],
)
data class ScanRunEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "folder_id") val folderId: String,
    @ColumnInfo(name = "options_json") val optionsJson: String,
    val state: String,
    val examined: Long = 0,
    val matched: Long = 0,
    val added: Long = 0,
    val known: Long = 0,
    val unreadable: Long = 0,
    @ColumnInfo(name = "directory_errors") val directoryErrors: Long = 0,
    @ColumnInfo(name = "started_at", defaultValue = "0") val startedAt: Long = 0,
    @ColumnInfo(name = "current_path", defaultValue = "''") val currentPath: String = "",
)
