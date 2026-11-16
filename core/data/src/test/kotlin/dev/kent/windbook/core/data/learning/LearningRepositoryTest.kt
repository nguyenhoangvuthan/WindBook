package dev.kent.windbook.core.data.learning

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LearningRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repo: LearningRepository
    private val anchor=PdfTextAnchor(page=0,width=400f,height=600f,rects=listOf(PdfTextRect(.1f,.2f,.5f,.3f)),quote="A quote")
    private val answer="""{"translation":"Bản dịch","grammar":"Grammar","idioms":"None","context":"Insufficient evidence","words":[{"term":"quote","meaning":"trích dẫn"}]}"""
    @Before fun setup()=runTest {
        db=WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(),AndroidSQLiteDriver())
        db.libraryDao().insert(BookEntity("b","Synthetic",null,"PDF",null,1,"sha",null,null,"TO_READ",1,null,1,null))
        repo=LearningRepository(db)
    }
    @After fun close()=db.close()
    @Test fun acceptedAnswerIsIdempotentAndVocabularyRetainsSourceWithoutReplacingNote()=runTest {
        val note=dev.kent.windbook.core.data.AnnotationRepository(db).save("b","sha",anchor,AnnotationColor.GOLD,"handwritten")
        repeat(2) {repo.accept("result","b","sha",anchor,null,note,answer,"passage-only")}
        assertEquals(1,repo.answers(note).first().size)
        assertEquals("handwritten",db.annotationDao().find(note)!!.body)
        repeat(2) {repo.addWord("result","quote","trích dẫn")}
        assertEquals(1,repo.vocabulary().first().size)
        assertEquals(note,repo.vocabulary().first().single().annotationId)
    }
    @Test fun staleAnswerCannotCreateAnnotationOrDurableData()=runTest {
        assertTrue(runCatching {repo.accept("result","b","stale",anchor,null,null,answer,"passage-only")}.isFailure)
        assertFalse(db.annotationDao().hasAnnotations("b"))
    }
    @Test fun preparationCheckpointsResumeAndRejectWrongRevision()=runTest {
        repo.beginPreparation("b","sha")
        repo.saveSummary("b","sha",0,"page:1","summary")
        repo.beginPreparation("b","sha")
        assertEquals(1,repo.preparation("b")!!.nextIndex)
        assertTrue(runCatching {repo.saveSummary("b","stale",1,"page:2","bad")}.isFailure)
        repo.finishPreparation("b","sha",1)
        assertTrue(repo.preparation("b")!!.complete)
    }
}
