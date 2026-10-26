package dev.kent.windbook.core.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="annotations", indices=[Index(value=["book_id"])], foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class AnnotationEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name="book_id") val bookId: String,
    val fingerprint: String,
    @ColumnInfo(name="anchor_json") val anchorJson: String,
    val quote: String,
    val color: String,
    val body: String?,
    @ColumnInfo(name="created_at") val createdAt: Long,
    @ColumnInfo(name="updated_at") val updatedAt: Long,
)
data class AnnotationRow(@Embedded val annotation: AnnotationEntity, @ColumnInfo(name="book_title") val bookTitle: String)

@Dao
interface AnnotationDao {
    @Query("SELECT annotations.*, books.title AS book_title FROM annotations JOIN books ON books.id = annotations.book_id WHERE books.deleted_at IS NULL AND (:bookId IS NULL OR annotations.book_id = :bookId) ORDER BY annotations.updated_at DESC, annotations.id ASC")
    fun observe(bookId: String?): Flow<List<AnnotationRow>>
    @Query("SELECT * FROM annotations WHERE id = :id") suspend fun find(id: String): AnnotationEntity?
    @Query("SELECT EXISTS(SELECT 1 FROM annotations WHERE book_id = :bookId)") suspend fun hasAnnotations(bookId: String): Boolean
    @Upsert suspend fun upsert(annotation: AnnotationEntity)
    @Query("DELETE FROM annotations WHERE id = :id") suspend fun delete(id: String)
}
