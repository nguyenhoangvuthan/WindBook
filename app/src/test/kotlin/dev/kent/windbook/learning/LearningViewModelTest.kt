package dev.kent.windbook.learning

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.learning.*
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.*
import dev.kent.windbook.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LearningViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private lateinit var db:WindBookDatabase
    private lateinit var vm:LearningViewModel
    private val session=object:ReaderSession {override val format=BookFormat.EPUB;override fun close() {}}
    private val anchor=EpubTextAnchor(href="c.xhtml",locatorJson="""{"href":"c.xhtml","type":"application/xhtml+xml","text":{"highlight":"Quote"}}""",quote="Quote")
    @Before fun setup()=runTest {
        db=WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(),AndroidSQLiteDriver())
        db.libraryDao().insert(BookEntity("b","Synthetic",null,"EPUB",null,1,"sha",null,null,"TO_READ",1,null,1,null))
        vm=LearningViewModel(db,ApplicationProvider.getApplicationContext())
    }
    @After fun close() {vm.dismiss();db.close()}
    @Test fun missingKeyKeepsSelectionAndDoesNotCallProvider()=runTest {
        var calls=0;vm.keyProvider={null};vm.client=GeminiClient {_,_->calls++;GeminiHttpResult(500,"")}
        vm.open(LearningTarget("b","sha",epub=anchor),session)
        vm.generate();vm.state.first {!it.working}
        assertEquals(AiFailure.KEY,vm.state.value.failure);assertEquals("Quote",vm.state.value.target?.quote);assertEquals(0,calls)
    }
    @Test fun cancelledOldRequestCannotReplaceNewPassage()=runTest {
        vm.keyProvider={"key"};val waiting=CompletableDeferred<GeminiHttpResult>()
        vm.client=GeminiClient {_,_->waiting.await()}
        vm.open(LearningTarget("b","sha",epub=anchor),session);vm.generate();runCurrent()
        vm.open(LearningTarget("b","sha",epub=anchor.copy(quote="Second")),session)
        waiting.complete(GeminiHttpResult(429,""));advanceUntilIdle()
        assertEquals("Second",vm.state.value.target?.quote);assertNull(vm.state.value.failure);assertFalse(vm.state.value.working)
    }
    @Test fun recreationRetainsAnswerAndNewFollowupUsesSameHighlight()=runTest {
        vm.keyProvider={"key"}
        val answer="""{"translation":"Dịch","grammar":"Grammar","idioms":"None","context":"Unknown","words":[]}"""
        val response=org.json.JSONObject().put("candidates",org.json.JSONArray().put(org.json.JSONObject().put("finishReason","STOP").put("content",org.json.JSONObject().put("parts",org.json.JSONArray().put(org.json.JSONObject().put("text",answer)))))).toString()
        vm.client=GeminiClient {_,_->GeminiHttpResult(200,response)}
        vm.open(LearningTarget("b","sha",epub=anchor),session);vm.generate();vm.state.first {!it.working}
        vm.save();vm.state.first {!it.working}
        val id=vm.state.value.savedAnnotationId!!
        vm.detach();vm.attach(session,"sha")
        assertNotNull(vm.state.value.answer)
        assertEquals(vm.state.value.resultId,vm.state.value.savedResultId)
        vm.generate("More");vm.state.first {!it.working}
        assertNotEquals(vm.state.value.resultId,vm.state.value.savedResultId)
        vm.save();vm.state.first {!it.working}
        assertEquals(vm.state.value.resultId,vm.state.value.savedResultId)
        assertEquals(id,vm.state.value.savedAnnotationId)
        assertEquals(1,db.annotationDao().observe("b").first().size)
    }

}
