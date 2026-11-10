package dev.kent.windbook.core.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="bookmarks",indices=[Index(value=["book_id"])],foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class BookmarkEntity(@PrimaryKey val id: String, @ColumnInfo(name="book_id") val bookId: String,
    val fingerprint: String, @ColumnInfo(name="locator_json") val locatorJson: String, val progression: Double,
    val label: String, @ColumnInfo(name="text_encoding") val textEncoding: String?,
    @ColumnInfo(name="created_at") val createdAt: Long, @ColumnInfo(name="updated_at") val updatedAt: Long,
    @ColumnInfo(name="deleted_at") val deletedAt: Long? = null)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE book_id=:bookId AND fingerprint=:sha AND text_encoding IS :encoding AND deleted_at IS NULL ORDER BY created_at DESC, id")
    fun observe(bookId: String, sha: String, encoding: String?): Flow<List<BookmarkEntity>>
    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE book_id=:bookId)") suspend fun hasBookmarks(bookId: String): Boolean
    @Query("SELECT * FROM bookmarks WHERE id=:id") suspend fun find(id: String): BookmarkEntity?
    @Upsert suspend fun upsert(bookmark: BookmarkEntity)
    @Query("UPDATE bookmarks SET label=:label, updated_at=:now WHERE id=:id AND deleted_at IS NULL") suspend fun rename(id: String,label: String,now: Long)
    @Query("UPDATE bookmarks SET deleted_at=:now, updated_at=:now WHERE id=:id") suspend fun remove(id: String,now: Long)
}

@Entity(tableName="pdf_contents",foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class PdfContentsEntity(@PrimaryKey @ColumnInfo(name="book_id") val bookId: String, val fingerprint: String,
    @ColumnInfo(name="payload_version") val payloadVersion: Int, val status: String,
    @ColumnInfo(name="contents_json") val contentsJson: String)

@Dao
interface PdfContentsDao {
    @Query("SELECT * FROM pdf_contents WHERE book_id=:bookId AND fingerprint=:sha AND payload_version=:version")
    suspend fun find(bookId: String,sha: String,version: Int): PdfContentsEntity?
    @Upsert suspend fun upsert(contents: PdfContentsEntity)
}
