package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryDaoTest {
    private lateinit var db: WindBookDatabase
    private lateinit var dao: LibraryDao

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        dao = db.libraryDao()
    }

    @After fun tearDown() = db.close()

    private fun book(id: String, sha: String = "sha-$id", status: String = "TO_READ") = BookEntity(
        id = id, title = "Book $id", author = null, format = "EPUB", filePath = "/f/$id.epub",
        fileSize = 10, sha256 = sha, coverPath = null, sourceUri = null, status = status,
        addedAt = 1, lastOpenedAt = null, updatedAt = 1, deletedAt = null,
    )

    @Test fun observeLibrary_joinsProgressAndExcludesDeleted() = runTest {
        dao.insert(book("a"))
        dao.insert(book("b"))
        dao.upsertProgress(ReadingProgressEntity("a", """{"href":"c1"}""", 0.25, 5))
        dao.softDelete("b", now = 9)

        val rows = dao.observeLibrary().first()

        assertEquals(listOf("a"), rows.map { it.book.id })
        assertEquals(0.25, rows.single().progression, 1e-9)
    }

    @Test fun unreadBookHasZeroProgression() = runTest {
        dao.insert(book("a"))
        assertEquals(0.0, dao.observeLibrary().first().single().progression, 1e-9)
    }

    @Test fun markOpened_promotesToReadToReadingAndKeepsFinished() = runTest {
        dao.insert(book("a"))
        dao.insert(book("f", status = "FINISHED"))

        dao.markOpened("a", now = 42)
        dao.markOpened("f", now = 43)

        assertEquals("READING", dao.findById("a")!!.status)
        assertEquals(42L, dao.findById("a")!!.lastOpenedAt)
        assertEquals("FINISHED", dao.findById("f")!!.status)
    }

    @Test fun softDelete_hidesBookRemovesProgressButFindByShaStillSeesIt() = runTest {
        dao.insert(book("a", sha = "same"))
        dao.upsertProgress(ReadingProgressEntity("a", "{}", 0.5, 1))

        dao.softDelete("a", now = 7)

        assertNull(dao.findById("a"))
        assertNull(dao.progress("a"))
        assertEquals(7L, dao.findBySha("same")!!.deletedAt)
    }

    @Test fun purge_cascadesProgress() = runTest {
        dao.insert(book("a"))
        dao.upsertProgress(ReadingProgressEntity("a", "{}", 0.5, 1))

        dao.purge("a")

        assertNull(dao.findBySha("sha-a"))
        assertNull(dao.progress("a"))
    }

    @Test fun sharedRevisionHashKeepsSeparateRowsAndDeterministicCanonicalLookup() = runTest {
        dao.insert(book("b", sha = "dup"))
        dao.insert(book("a", sha = "dup"))
        assertEquals(setOf("a", "b"), dao.allIdsIncludingHidden().toSet())
        assertEquals("a", dao.findBySha("dup")!!.id)
        dao.tombstone("a", 10)
        assertEquals("b", dao.findBySha("dup")!!.id)
    }

    @Test fun upsertProgress_overwrites() = runTest {
        dao.insert(book("a"))
        dao.upsertProgress(ReadingProgressEntity("a", "{\"v\":1}", 0.1, 1))
        dao.upsertProgress(ReadingProgressEntity("a", "{\"v\":2}", 0.2, 2))
        val p = dao.progress("a")!!
        assertTrue(p.locatorJson.contains("2"))
        assertEquals(0.2, p.progression, 1e-9)
    }
}
