package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
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
class ReaderControlsRepositoryTest {
    private lateinit var db: WindBookDatabase
    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        db.libraryDao().insert(BookEntity("b","Book",null,"PDF","/f",1,"sha",null,null,"TO_READ",1,null,1,null))
    }
    @After fun tearDown() = db.close()
    @Test fun bookmarksRequireCurrentFingerprintAndAreIdempotent() = runTest {
        val repo=BookmarkRepository(db)
        assertNull(repo.save("b","wrong","{}",.2,"Old",null))
        val first=repo.save("b","sha","{\"href\":\"publication.pdf\",\"locations\":{\"position\":2}}",.2,"Page",null)
        val again=repo.save("b","sha","{\"href\":\"publication.pdf\",\"locations\":{\"position\":2}}",.2,"Page",null)
        assertEquals(first,again)
        assertEquals(1,repo.observe("b","sha",null).first().size)
        assertTrue(repo.observe("b","different",null).first().isEmpty())
        repo.rename(first!!,"Renamed")
        assertEquals("Renamed",repo.observe("b","sha",null).first().single().label)
        repo.remove(first)
        assertTrue(repo.observe("b","sha",null).first().isEmpty())
    }
    @Test fun contentsCacheCannotCrossRevisionsAndKeepsKnownEmpty() = runTest {
        val repo=PdfContentsRepository(db)
        assertNull(repo.find("b","sha"))
        assertFalse(repo.save("b","wrong",PdfContents(ContentsStatus.EMPTY)))
        assertTrue(repo.save("b","sha",PdfContents(ContentsStatus.EMPTY)))
        assertEquals(ContentsStatus.EMPTY,repo.find("b","sha")!!.status)
        assertNull(repo.find("b","other"))
    }
    @Test fun atomicTypographyAndPdfUpdatesKeepUnknownKeys() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b","{\"future\":7}",1))
        val typography=BookTypographyRepository(db.bookPreferencesDao())
        val pdf=DisplayPreferencesRepository(db.bookPreferencesDao())
        typography.set("b",ReaderTypography(size=150))
        pdf.update("b") { it.copy(pdfLayout=PdfLayout.PAGED) }
        typography.set("b",ReaderTypography(size=180))
        assertEquals(180,typography.observe("b").first().size)
        assertEquals(PdfLayout.PAGED,pdf.observe("b").first().pdfLayout)
        assertTrue(db.bookPreferencesDao().json("b")!!.contains("\"future\":7"))
        typography.set("b",ReaderTypography())
        assertEquals(PdfLayout.PAGED,pdf.observe("b").first().pdfLayout)
    }
}
