package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.BookPreferencesEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.PdfLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisplayPreferencesRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repo: DisplayPreferencesRepository

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        repo = DisplayPreferencesRepository(db.bookPreferencesDao(), clock = { 7L })
        db.libraryDao().insert(BookEntity("b", "Book", null, "PDF", "/f", 1, "sha", null, null, "TO_READ", 1, null, 1, null))
    }

    @After fun tearDown() = db.close()

    @Test fun unknownBookHasDefaults() = runTest {
        assertEquals(BookDisplayPrefs(), repo.observe("b").first())
    }

    @Test fun choicesRoundTrip() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, cropMargins = false))
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false), repo.observe("b").first())
    }

    @Test fun unknownKeysFromLaterVersionsSurviveAWrite() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", """{"fontScale":1.2,"pdfLayout":"VERTICAL"}""", 1))
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, null))
        val json = JSONObject(db.bookPreferencesDao().json("b")!!)
        assertEquals(1.2, json.getDouble("fontScale"), 1e-9)
        assertEquals("PAGED", json.getString("pdfLayout"))
    }

    @Test fun garbageJsonFallsBackToDefaults() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", "{broken", 1))
        assertEquals(BookDisplayPrefs(), repo.observe("b").first())
    }

    @Test fun deletingTheBookDeletesItsPreferences() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, null))
        db.libraryDao().purge("b")
        assertNull(db.bookPreferencesDao().json("b"))
    }

    @Test fun zoomAndReflowSizeRoundTrip() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.VERTICAL, true, zoom = 2.5f, zoomX = 0.4f, reflowSize = 5))
        assertEquals(BookDisplayPrefs(PdfLayout.VERTICAL, true, 2.5f, 0.4f, 5), repo.observe("b").first())
    }

    @Test fun preferencesSavedBeforeZoomExistedHaveNoZoom() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", """{"pdfLayout":"PAGED","cropMargins":false}""", 1))
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false), repo.observe("b").first())
    }
}
