package dev.kent.windbook.core.data.learning

import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.AnnotationRepository
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import java.util.UUID

class LearningRepository(private val db:WindBookDatabase) {
    private val dao=db.learningDao()
    fun answers(annotationId:String)=dao.answers(annotationId)
    fun vocabulary()=dao.vocabulary()
    suspend fun deleteWord(id:String)=dao.deleteWord(id)
    suspend fun accept(id:String,bookId:String,fingerprint:String,pdf:PdfTextAnchor?,epub:EpubTextAnchor?,annotationId:String?,payload:String,contextVersion:String):String = db.withWriteTransaction {
        LearningAnswer.parse(payload);require(contextVersion.length<=256)
        require(db.libraryDao().findById(bookId)?.sha256==fingerprint && fingerprint.isNotBlank())
        dao.answer(id)?.let {existing ->
            val note=requireNotNull(db.annotationDao().find(existing.annotationId))
            require(note.bookId==bookId && existing.fingerprint==fingerprint && existing.payload==payload)
            return@withWriteTransaction existing.annotationId
        }
        val noteId=if(annotationId!=null) {
            val note=requireNotNull(db.annotationDao().find(annotationId))
            require(note.bookId==bookId && note.fingerprint==fingerprint);note.id
        } else {
            require((pdf!=null) xor (epub!=null))
            val notes=AnnotationRepository(db)
            if(epub!=null) notes.saveEpub(bookId,fingerprint,epub,AnnotationColor.GOLD,null)
            else notes.save(bookId,fingerprint,requireNotNull(pdf),AnnotationColor.GOLD,null)
        }
        dao.insertAnswer(LearningAnswerEntity(id,noteId,fingerprint,GeminiProtocol.MODEL,GeminiProtocol.PROMPT_VERSION,contextVersion,payload,System.currentTimeMillis()))
        noteId
    }
    suspend fun addWord(answerId:String,term:String,meaning:String)=db.withWriteTransaction {
        val answer=requireNotNull(dao.answer(answerId))
        require(LearningAnswer.parse(answer.payload).words.any {it.term==term && it.meaning==meaning})
        val id=UUID.nameUUIDFromBytes("$answerId\u0000$term\u0000$meaning".toByteArray()).toString()
        dao.insertWord(VocabularyEntity(id,answerId,term,meaning,System.currentTimeMillis()))
    }
    suspend fun preparation(bookId:String)=dao.preparation(bookId)
    suspend fun summaries(bookId:String)=dao.summaries(bookId)
    suspend fun beginPreparation(bookId:String,fingerprint:String)=db.withWriteTransaction {
        require(db.libraryDao().findById(bookId)?.sha256==fingerprint && fingerprint.isNotBlank())
        val old=dao.preparation(bookId)
        if(old==null || old.fingerprint!=fingerprint || old.version!=1 || old.model!=GeminiProtocol.MODEL) {
            dao.deletePreparation(bookId)
            dao.upsertPreparation(LearningPreparationEntity(bookId,fingerprint,GeminiProtocol.MODEL,1,0,false))
        }
    }
    suspend fun saveSummary(bookId:String,fingerprint:String,index:Int,location:String,summary:String)=db.withWriteTransaction {
        val state=requireNotNull(dao.preparation(bookId))
        require(db.libraryDao().findById(bookId)?.sha256==fingerprint && state.fingerprint==fingerprint && !state.complete)
        require(state.nextIndex==index && index in 0 until 2048 && location.length<=8192 && summary.isNotBlank() && summary.length<=4096)
        dao.upsertSummary(LearningSummaryEntity(bookId,index,location,summary))
        dao.upsertPreparation(state.copy(nextIndex=index+1))
    }
    suspend fun saveOverview(bookId:String,fingerprint:String,summary:String)=db.withWriteTransaction {
        val state=requireNotNull(dao.preparation(bookId))
        require(state.fingerprint==fingerprint && db.libraryDao().findById(bookId)?.sha256==fingerprint && summary.isNotBlank() && summary.length<=4096)
        dao.upsertSummary(LearningSummaryEntity(bookId,-1,"overview",summary))
    }
    suspend fun finishPreparation(bookId:String,fingerprint:String,count:Int)=db.withWriteTransaction {
        val state=requireNotNull(dao.preparation(bookId))
        require(state.fingerprint==fingerprint && state.nextIndex==count && db.libraryDao().findById(bookId)?.sha256==fingerprint)
        dao.upsertPreparation(state.copy(complete=true))
    }
}
