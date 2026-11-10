package dev.kent.windbook.reader

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.*
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.metadata.PdfContentsExtractor
import dev.kent.windbook.reader.pdf.PdfPageSource
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderControlsViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    @get:Rule val tmp=TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private val store=ViewModelStore()
    @Before fun setUp()=runTest {
        db=WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(),AndroidSQLiteDriver())
        db.libraryDao().insert(BookEntity("b","Book",null,"PDF","/f",1,"sha",null,null,"TO_READ",1,null,1,null))
    }
    @After fun tearDown() {store.clear();db.close()}
    private class Pdf : PdfSession {
        override val format=BookFormat.PDF
        override val pageCount=10
        override val pages: PdfPageSource get()=error("Navigation must not render previews")
        override suspend fun fingerprint()="sha"
        override suspend fun firstPageSizes()=listOf(PageSize(400f,600f))
        override fun locatorJsonFor(page: Int)="""{"href":"publication.pdf","type":"application/pdf","locations":{"position":${page+1}}}"""
        override fun pageOf(locatorJson: String?)=locatorJson?.let {org.json.JSONObject(it).getJSONObject("locations").getInt("position")-1} ?: 0
        override fun close()=Unit
    }
    @Test fun failedJumpsHaveNoHistoryAndSuccessfulJumpCanReturnOnce()=runTest {
        val vm=create(backgroundScope);val pdf=Pdf()
        vm.bind(pdf,null,pdf.locatorJsonFor(0),.1)
        vm.seek(1.0)
        val failed=vm.request.first {it!=null}!!
        vm.completed(failed.id,false)
        assertFalse(vm.canReturn.value)
        vm.seek(1.0)
        val success=vm.request.first {it!=null}!!
        assertEquals(9,pdf.pageOf(success.locatorJson))
        vm.track(success.locatorJson,1.0,null);vm.completed(success.id,true)
        assertTrue(vm.canReturn.value)
        vm.backToLocation()
        val back=vm.request.first {it!=null}!!
        assertEquals(0,pdf.pageOf(back.locatorJson))
        vm.track(back.locatorJson,.1,null);vm.completed(back.id,true)
        assertFalse(vm.canReturn.value)
    }
    @Test fun sessionReplacementInvalidatesOldNavigationCallback()=runTest {
        val vm=create(backgroundScope);val old=Pdf()
        vm.bind(old,null,old.locatorJsonFor(0),.1);vm.seek(.5)
        val stale=vm.request.first {it!=null}!!
        val replacement=Pdf();vm.bind(replacement,null,replacement.locatorJsonFor(0),.1)
        vm.completed(stale.id,true)
        assertNull(vm.request.value)
        assertFalse(vm.canReturn.value)
    }
    @Test fun repeatedBookmarkCreationUsesOneVerifiedAnchor()=runTest {
        val vm=create(backgroundScope);val pdf=Pdf()
        vm.bind(pdf,null,pdf.locatorJsonFor(0),.1)
        vm.canBookmark.first {it}
        vm.bookmark();vm.bookmarks.first {it.size==1}
        vm.bookmark()
        assertEquals(1,vm.bookmarks.first {it.isNotEmpty()}.size)
    }
    @Test fun newestSeekWinsWhenOlderResolutionFinishesLate()=runTest {
        val started=kotlinx.coroutines.CompletableDeferred<Unit>()
        val release=kotlinx.coroutines.CompletableDeferred<Unit>()
        val navigation=object: ReaderNavigationAccess {
            override suspend fun position(session: ReaderSession,fraction: Double): String {
                if(fraction==.2) {started.complete(Unit);kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {release.await()}}
                return "target-$fraction"
            }
            override suspend fun search(session: ReaderSession,query: String): ReaderSearchCursor?=null
        }
        val vm=create(backgroundScope,navigation)
        val session=object: ReaderSession {override val format=BookFormat.EPUB;override fun close()=Unit}
        vm.bind(session,null,"origin",.1)
        vm.seek(.2);started.await();vm.seek(.8)
        val newer=vm.request.first {it?.locatorJson=="target-0.8"}!!
        vm.track(newer.locatorJson,.8,null);vm.completed(newer.id,true)
        release.complete(Unit)
        runCurrent()
        assertNull("Old resolution must not publish after the new jump",vm.request.value)
    }
    @Test fun clearingViewModelJoinsAnActiveSearchBeforeClosingExactlyOnce()=runTest {
        val entered=kotlinx.coroutines.CompletableDeferred<Unit>()
        val release=kotlinx.coroutines.CompletableDeferred<Unit>()
        val closed=kotlinx.coroutines.CompletableDeferred<Unit>()
        var closes=0
        val navigation=object: ReaderNavigationAccess {
            override suspend fun position(session: ReaderSession,fraction: Double): String?=null
            override suspend fun search(session: ReaderSession,query: String)=object: ReaderSearchCursor {
                override val exhausted=false
                override suspend fun next(): List<ReaderSearchHit> {
                    entered.complete(Unit)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {release.await()}
                    return emptyList()
                }
                override fun close() {closes++;closed.complete(Unit)}
            }
        }
        val vm=create(backgroundScope,navigation)
        vm.bind(object: ReaderSession {override val format=BookFormat.EPUB;override fun close()=Unit},null,null,0.0)
        vm.runSearch("text");advanceTimeBy(300);runCurrent();entered.await()
        store.clear();assertEquals(0,closes)
        release.complete(Unit)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {kotlinx.coroutines.withTimeout(2000) {closed.await()}}
        assertTrue("Search resource cleanup must survive UI scope cancellation",closed.isCompleted)
        assertEquals(1,closes)
    }
    private fun create(scope: kotlinx.coroutines.CoroutineScope,navigation: ReaderNavigationAccess=DefaultReaderNavigationAccess): ReaderControlsViewModel {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val preferences=PreferenceDataStoreFactory.create(scope=scope,produceFile={tmp.root.resolve("reader.preferences_pb")})
        return ReaderControlsViewModel("b",LibraryRepository(db.libraryDao(),BookFileStore(tmp.newFolder())),BookmarkRepository(db),PdfContentsRepository(db),
            PdfContentsExtractor(ReadiumServices(context)),SourceAccess(context),BookTypographyRepository(db.bookPreferencesDao()),ReaderPreferencesRepository(preferences),navigation,scope).also {store.put("vm",it)}
    }
}
