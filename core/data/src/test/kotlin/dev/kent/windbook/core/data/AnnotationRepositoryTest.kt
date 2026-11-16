package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnnotationRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repo: AnnotationRepository
    private val anchor = PdfTextAnchor(page=2,width=400f,height=600f,rects=listOf(PdfTextRect(.1f,.2f,.5f,.3f)),quote="Tiếng Việt")
    @Before fun setup() = runTest {
        db=WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(),AndroidSQLiteDriver())
        repo=AnnotationRepository(db,clock={7})
        db.libraryDao().insert(BookEntity("b","Synthetic",null,"PDF",null,1,"sha",null,null,"TO_READ",1,null,1,null))
    }
    @After fun close() = db.close()
    @Test fun highlightNoteAndColorRoundTripByIdWithoutChangingAnchor() = runTest {
        val id=repo.save("b","sha",anchor,AnnotationColor.GOLD,null)
        repo.update(id,AnnotationColor.BLUE,"  A note  ")
        val found=repo.observe().first().single()
        assertEquals(id,found.id); assertEquals("A note",found.body)
        assertEquals(anchor,found.anchor); assertEquals(AnnotationColor.BLUE,found.color)
        assertEquals("Synthetic",found.bookTitle)
        repo.delete(id)
        assertTrue(repo.observe().first().isEmpty())
    }
    @Test fun blankBodyInvalidAnchorAndRevisionMismatchDoNotWrite() = runTest {
        for (action in listOf<suspend () -> Unit>(
            { repo.save("b","sha",anchor,AnnotationColor.GOLD," ") },
            { repo.save("b","sha",anchor.copy(page=-1),AnnotationColor.GOLD,null) },
            { repo.save("b","other",anchor,AnnotationColor.GOLD,null) },
        )) assertTrue(runCatching { action() }.isFailure)
        assertTrue(repo.observe().first().isEmpty())
    }
    @Test fun softDeleteRetainsAndRevivesNotesAndPurgeCascades() = runTest {
        val id=repo.save("b","sha",anchor,AnnotationColor.GOLD,"note")
        db.libraryDao().tombstone("b",8)
        assertTrue(repo.observe().first().isEmpty()); assertNotNull(repo.find(id))
        db.libraryDao().update(db.libraryDao().findIncludingHidden("b")!!.copy(deletedAt=null))
        assertEquals(id,repo.observe().first().single().id)
        db.libraryDao().purge("b"); assertNull(repo.find(id))
    }
    @Test fun malformedAnchorLeavesQuoteAndNoteReadable() = runTest {
        val id=repo.save("b","sha",anchor,AnnotationColor.GOLD,"note")
        db.annotationDao().upsert(db.annotationDao().find(id)!!.copy(anchorJson="{broken"))
        val item=repo.observe().first().single()
        assertNull(item.anchor); assertEquals("note",item.body); assertEquals(anchor.quote,item.quote)
    }
    @Test fun notesAreDurableDataWhenUnreadSourceWouldOtherwiseRedirect() = runTest {
        // Use a valid saved record then clear the old fingerprint to exercise the durable-data branch.
        repo.save("b","sha",anchor,AnnotationColor.GOLD,null)
        db.libraryDao().update(db.libraryDao().findById("b")!!.copy(sha256=null))
        db.libraryDao().insert(BookEntity("canonical","Other",null,"PDF",null,1,"same",null,null,"TO_READ",1,null,1,null))
        val source=BookSource.External("content://test/document/b",DocumentKey("test","b"))
        val sources=BookSourcesRepository(db,FolderRepository(db,{emptySet()},{},{_,_->}))
        assertEquals(Verification.Ready("b",source),sources.verify("b",source,"same"))
        assertEquals("b",repo.observe().first().single().bookId)
        val changed=sources.verify("b",source,"changed") as Verification.Changed
        assertTrue(repo.observe(changed.newBookId).first().isEmpty())
    }
    @Test fun epubAnchorRoundTripsAndRejectsStaleOrInconsistentLocators() = runTest {
        db.libraryDao().insert(BookEntity("e","Synthetic",null,"EPUB",null,1,"sha",null,null,"TO_READ",1,null,1,null))
        val a=EpubTextAnchor(href="chapter.xhtml",locatorJson="""{"href":"chapter.xhtml","type":"application/xhtml+xml","text":{"highlight":"A sentence"}}""",quote="A sentence")
        val id=repo.saveEpub("e","sha",a,AnnotationColor.GREEN,"note")
        assertEquals(a,repo.find(id)!!.epubAnchor)
        assertNull(repo.find(id)!!.anchor)
        assertEquals("EPUB_TEXT",repo.find(id)!!.kind)
        assertTrue(runCatching {repo.saveEpub("e","stale",a,AnnotationColor.GOLD,null)}.isFailure)
        assertTrue(runCatching {repo.saveEpub("e","sha",a.copy(quote="other"),AnnotationColor.GOLD,null)}.isFailure)
        db.annotationDao().upsert(db.annotationDao().find(id)!!.copy(kind="FUTURE"))
        assertFalse(repo.find(id)!!.canReveal)
        assertEquals("note",repo.find(id)!!.body)
    }

}
