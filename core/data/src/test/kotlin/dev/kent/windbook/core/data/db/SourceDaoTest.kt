package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceDaoTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private val released = mutableListOf<String>()
    private lateinit var repo: BookSourcesRepository
    private val treeA = "content://provider/tree/a"
    private val treeB = "content://provider/tree/b"

    @Before fun setup() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val held = mutableSetOf(treeA, treeB)
        val folders = FolderRepository(db, { held.toSet() }, { released.add(it); held.remove(it) }, { _, _ -> error("No picker acquisition expected") }) { 77 }
        repo = BookSourcesRepository(db, folders, clock = { 77 })
    }
    @After fun close() = db.close()

    private fun book(id: String) = BookEntity(id, "$id.epub", null, "EPUB", null, 0, null, null, null,
        "TO_READ", 1, null, 1, null)
    private suspend fun sharedSource() {
        db.libraryDao().insert(book("book"))
        db.folderDao().upsert(FolderEntity("a", treeA, "A", "[\"EPUB\"]", true))
        db.folderDao().upsert(FolderEntity("b", treeB, "B", "[\"EPUB\"]", true))
        db.sourceDao().insert(BookSourceEntity("s", "book", "EXTERNAL", uri = "$treeA/document/42",
            authority = "provider", documentId = "42", availability = "AVAILABLE"))
        db.folderDao().upsertMembership(FolderMembershipEntity("a", "s", "child/book.epub", "child", "run-a"))
        db.folderDao().upsertMembership(FolderMembershipEntity("b", "s", "book.epub", "b", "run-b"))
    }

    @Test fun unhashedCandidates_remainDistinctAndOverlapUsesOneSource() = runTest {
        sharedSource()
        db.libraryDao().insert(book("other"))
        db.sourceDao().insert(BookSourceEntity("s2", "other", "EXTERNAL", uri = "$treeB/document/43",
            authority = "provider", documentId = "43"))
        assertEquals(setOf("book", "other"), db.libraryDao().allIdsIncludingHidden().toSet())
        assertEquals(2, db.folderDao().memberships("s").size)
        assertEquals(listOf(BookSource.External("$treeA/document/42", DocumentKey("provider", "42"))), repo.sources("book"))
        assertNull(db.libraryDao().findById("other")!!.sha256)
        try {
            db.sourceDao().insert(BookSourceEntity("duplicate", "other", "EXTERNAL", uri = "$treeB/document/42",
                authority = "provider", documentId = "42"))
            fail("The same provider document must have one canonical source")
        } catch (_: android.database.SQLException) { }
        assertEquals(1, db.sourceDao().forBook("other").size)
    }

    @Test fun hideAndRestore_preserveReadingDataAndMemberships() = runTest {
        sharedSource()
        db.libraryDao().upsertProgress(ReadingProgressEntity("book", "old-locator", 0.4, 2))
        db.bookPreferencesDao().upsert(BookPreferencesEntity("book", "{\"unknown\":42}", 3))
        repo.hideExternalBook("book")
        assertNull(db.libraryDao().findById("book"))
        assertTrue(db.libraryDao().observeLibrary().first().isEmpty())
        repo.restoreExternalBook("book")
        assertNotNull(db.libraryDao().findById("book"))
        assertEquals("old-locator", db.libraryDao().progress("book")!!.locatorJson)
        assertEquals("{\"unknown\":42}", db.bookPreferencesDao().json("book"))
        assertEquals(2, db.folderDao().memberships("s").size)
        assertTrue(released.isEmpty())
    }

    @Test fun disconnect_preservesSharedSourceAndRetainsItsRequiredGrant() = runTest {
        sharedSource()
        db.libraryDao().upsertProgress(ReadingProgressEntity("book", "old-locator", 0.4, 2))
        repo.disconnectFolder("a")
        assertEquals(77L, db.folderDao().find("a")!!.disconnectedAt)
        assertNull(db.folderDao().find("b")!!.disconnectedAt)
        assertEquals("AVAILABLE", db.sourceDao().forBook("book").single().availability)
        assertTrue(released.isEmpty())
        assertEquals("old-locator", db.libraryDao().progress("book")!!.locatorJson)
        assertEquals(2, db.folderDao().memberships("s").size)
        assertNotNull(db.libraryDao().findById("book"))
    }

    @Test fun disconnect_releasesUnusedDistinctGrantAndMarksOnlyOrphanSources() = runTest {
        sharedSource()
        repo.disconnectFolder("b")
        assertEquals(listOf(treeB), released)
        assertEquals("AVAILABLE", db.sourceDao().forBook("book").single().availability)
        repo.disconnectFolder("a")
        assertEquals(listOf(treeB, treeA), released)
        assertEquals("DISCONNECTED", db.sourceDao().forBook("book").single().availability)
        assertEquals(2, db.folderDao().memberships("s").size)
    }

    @Test fun scanRunAndMembershipDetails_surviveDatabaseReopen() = runTest {
        db.close()
        val path = File(tmp.root, "sources.db").absolutePath
        db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        sharedSource()
        val run = ScanRunEntity("run-a", "a", "{\"recursive\":true}", "INCOMPLETE", examined = 123,
            matched = 80, added = 50, known = 30, unreadable = 2, directoryErrors = 1)
        db.folderDao().upsertRun(run)
        db.close()
        db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        assertEquals(run, db.folderDao().run("run-a"))
        val membership = db.folderDao().memberships("s").first { it.folderId == "a" }
        assertEquals("child/book.epub", membership.relativePath)
        assertEquals("child", membership.parentDocumentId)
        assertEquals("run-a", membership.lastSeenRun)
    }
}
