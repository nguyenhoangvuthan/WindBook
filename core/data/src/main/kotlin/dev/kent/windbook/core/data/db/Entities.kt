package dev.kent.windbook.core.data.db

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["sha256"], unique = true),
        Index(value = ["last_opened_at"]),
    ],
)
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String?,
    val format: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "file_size") val fileSize: Long,
    val sha256: String,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    @ColumnInfo(name = "source_uri") val sourceUri: String?,
    val status: String,
    @ColumnInfo(name = "added_at") val addedAt: Long,
    @ColumnInfo(name = "last_opened_at") val lastOpenedAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
)

@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    @ColumnInfo(name = "locator_json") val locatorJson: String,
    val progression: Double,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

data class BookWithProgressRow(
    @Embedded val book: BookEntity,
    val progression: Double,
)

@Entity(
    tableName = "book_preferences",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    val json: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
