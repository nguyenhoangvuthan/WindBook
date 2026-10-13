package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ReadingStatus
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var bookFile: File
    private lateinit var coverFile: File

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store, clock = { 77L })
        bookFile = tmp.newFile("a.epub")
        coverFile = tmp.newFile("a.webp")
        db.libraryDao().insert(
            BookEntity("a", "Dune", "Frank Herbert", "EPUB", bookFile.absolutePath, 1, "sha", coverFile.absolutePath,
                null, "TO_READ", 1, null, 1, null),
        )
    }

    @After fun tearDown() = db.close()

    @Test fun observeLibrary_mapsToDomain() = runTest {
        val item = repo.observeLibrary().first().single()
        assertEquals("Dune", item.book.title)
        assertEquals(BookFormat.EPUB, item.book.format)
        assertEquals(ReadingStatus.TO_READ, item.book.status)
        assertEquals(0.0, item.progression, 1e-9)
    }

    @Test fun saveProgress_thenSavedLocatorAndProgression() = runTest {
        repo.saveProgress("a", """{"href":"c1"}""", 0.37)
        assertEquals("""{"href":"c1"}""", repo.savedLocator("a"))
        assertEquals(0.37, repo.observeLibrary().first().single().progression, 1e-9)
    }

    @Test fun markOpened_setsReadingAndTimestamp() = runTest {
        repo.markOpened("a")
        val book = repo.book("a")!!
        assertEquals(ReadingStatus.READING, book.status)
        assertEquals(77L, book.lastOpenedAt)
    }

    @Test fun setStatus_finished() = runTest {
        repo.setStatus("a", ReadingStatus.FINISHED)
        assertEquals(ReadingStatus.FINISHED, repo.book("a")!!.status)
    }

    @Test fun delete_removesFilesAndHidesBook() = runTest {
        repo.saveProgress("a", "{}", 0.5)
        repo.delete("a")
        assertNull(repo.book("a"))
        assertFalse(bookFile.exists())
        assertFalse(coverFile.exists())
        assertEquals(emptyList<Any>(), repo.observeLibrary().first())
        assertNull(repo.savedLocator("a"))
    }
}
