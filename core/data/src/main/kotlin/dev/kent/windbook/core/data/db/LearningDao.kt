package dev.kent.windbook.core.data.db

import androidx.room3.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="learning_answers",indices=[Index(value=["annotation_id"])],foreignKeys=[ForeignKey(entity=AnnotationEntity::class,parentColumns=["id"],childColumns=["annotation_id"],onDelete=ForeignKey.CASCADE)])
data class LearningAnswerEntity(@PrimaryKey val id:String,@ColumnInfo(name="annotation_id") val annotationId:String,
    val fingerprint:String,val model:String,@ColumnInfo(name="prompt_version") val promptVersion:Int,
    @ColumnInfo(name="context_version") val contextVersion:String,val payload:String,@ColumnInfo(name="created_at") val createdAt:Long)
@Entity(tableName="vocabulary",indices=[Index(value=["answer_id"])],foreignKeys=[ForeignKey(entity=LearningAnswerEntity::class,parentColumns=["id"],childColumns=["answer_id"],onDelete=ForeignKey.CASCADE)])
data class VocabularyEntity(@PrimaryKey val id:String,@ColumnInfo(name="answer_id") val answerId:String,val term:String,val meaning:String,@ColumnInfo(name="created_at") val createdAt:Long)
data class VocabularyRow(@Embedded val word:VocabularyEntity,@ColumnInfo(name="annotation_id") val annotationId:String,@ColumnInfo(name="book_id") val bookId:String)
@Entity(tableName="learning_preparations",foreignKeys=[ForeignKey(entity=BookEntity::class,parentColumns=["id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class LearningPreparationEntity(@PrimaryKey @ColumnInfo(name="book_id") val bookId:String,val fingerprint:String,val model:String,val version:Int,@ColumnInfo(name="next_index") val nextIndex:Int,val complete:Boolean)
@Entity(tableName="learning_summaries",primaryKeys=["book_id","section_index"],foreignKeys=[ForeignKey(entity=LearningPreparationEntity::class,parentColumns=["book_id"],childColumns=["book_id"],onDelete=ForeignKey.CASCADE)])
data class LearningSummaryEntity(@ColumnInfo(name="book_id") val bookId:String,@ColumnInfo(name="section_index") val sectionIndex:Int,val location:String,val summary:String)

@Dao
interface LearningDao {
    @Query("SELECT * FROM learning_answers WHERE annotation_id=:id ORDER BY created_at DESC") fun answers(id:String):Flow<List<LearningAnswerEntity>>
    @Query("SELECT * FROM learning_answers WHERE id=:id") suspend fun answer(id:String):LearningAnswerEntity?
    @Insert suspend fun insertAnswer(value:LearningAnswerEntity)
    @Query("SELECT vocabulary.*, annotations.id AS annotation_id,annotations.book_id AS book_id FROM vocabulary JOIN learning_answers ON learning_answers.id=vocabulary.answer_id JOIN annotations ON annotations.id=learning_answers.annotation_id JOIN books ON books.id=annotations.book_id WHERE books.deleted_at IS NULL ORDER BY vocabulary.created_at DESC") fun vocabulary():Flow<List<VocabularyRow>>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insertWord(value:VocabularyEntity)
    @Query("DELETE FROM vocabulary WHERE id=:id") suspend fun deleteWord(id:String)
    @Query("SELECT * FROM learning_preparations WHERE book_id=:id") suspend fun preparation(id:String):LearningPreparationEntity?
    @Upsert suspend fun upsertPreparation(value:LearningPreparationEntity)
    @Query("DELETE FROM learning_preparations WHERE book_id=:id") suspend fun deletePreparation(id:String)
    @Query("SELECT * FROM learning_summaries WHERE book_id=:id ORDER BY section_index") suspend fun summaries(id:String):List<LearningSummaryEntity>
    @Upsert suspend fun upsertSummary(value:LearningSummaryEntity)
}
