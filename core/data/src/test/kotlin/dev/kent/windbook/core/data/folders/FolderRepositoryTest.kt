package dev.kent.windbook.core.data.folders

import android.content.Context
import android.content.Intent
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repo: FolderRepository
    private lateinit var grants: FolderGrants
    private val a = "content://repository.test/tree/a"
    private val b = "content://repository.test/tree/b"

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        grants = FolderGrants(context.contentResolver)
        repo = FolderRepository(db, grants::persistedReadTrees, grants::releaseRead, grants::persistRead, clock = { 77 })
    }
    @After fun close() = db.close()
    private fun grant(tree: String) = grants.persistRead(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)

    @Test fun save_reusesTreeIdentityAndRemembersOptionsWithoutChangingLastScan() = runTest {
        grant(a)
        val id = repo.save(a, "A", ScanOptions())
        assertEquals(id, repo.save(a, "Renamed", ScanOptions(setOf(BookFormat.TXT), false)))
        val folder = repo.observeFolders().first().single()
        assertEquals("Renamed", folder.displayLocation)
        assertEquals(ScanOptions(setOf(BookFormat.TXT), false), folder.options)
        assertTrue(folder.connected)
        assertNull(folder.lastScanAt)
        repo.disconnectFolder(id)
        assertFalse(repo.observeFolders().first().single().connected)
        assertEquals(id, repo.connect(a, "Again", ScanOptions(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(repo.observeFolders().first().single().connected)
    }

    @Test fun disconnect_sweepsRetainedGrantOnlyAfterLastConnectedMembershipAndKeepsReadingData() = runTest {
        grant(a); grant(b)
        val fa = repo.save(a, "A", ScanOptions())
        val fb = repo.save(b, "B", ScanOptions())
        db.libraryDao().insert(BookEntity("book", "Synthetic.pdf", null, "PDF", null, 0, null, null, null,
            "READING", 1, 1, 1, null))
        db.sourceDao().insert(BookSourceEntity("source", "book", "EXTERNAL", uri = "$a/document/shared",
            authority = "repository.test", documentId = "shared", availability = "AVAILABLE"))
        for (folder in listOf(fa, fb)) db.folderDao().upsertMembership(FolderMembershipEntity(folder, "source", "Synthetic.pdf", "a", "old"))
        db.libraryDao().upsertProgress(ReadingProgressEntity("book", "locator", 0.5, 1))
        db.bookPreferencesDao().upsert(BookPreferencesEntity("book", "{\"unknown\":17}", 1))
        val sources = BookSourcesRepository(db, repo)
        sources.disconnectFolder(fa)
        assertTrue(grants.hasRead(a)); assertTrue(grants.hasRead(b))
        sources.disconnectFolder(fb)
        assertFalse(grants.hasRead(a)); assertFalse(grants.hasRead(b))
        assertEquals("DISCONNECTED", db.sourceDao().forBook("book").single().availability)
        assertEquals(2, db.folderDao().memberships("source").size)
        assertEquals("locator", db.libraryDao().progress("book")!!.locatorJson)
        assertEquals("{\"unknown\":17}", db.bookPreferencesDao().json("book"))
    }

    @Test fun reconnectAsFirstOperation_keepsNewlyAcquiredReadPermission() = runTest {
        db.folderDao().upsert(FolderEntity("a", a, "Old", "[]", true, disconnectedAt = 1, lastScanAt = 5))
        grant(a)
        assertEquals("a", repo.connect(a, "Reconnected", ScanOptions(), Intent.FLAG_GRANT_READ_URI_PERMISSION))
        assertTrue(grants.hasRead(a))
        assertNull(db.folderDao().find("a")!!.disconnectedAt)
        assertEquals(5L, db.folderDao().find("a")!!.lastScanAt)
    }

    @Test fun reconnectAndScanFinalization_doNotSweepReacquiredReadPermission() = runTest {
        val acquired = CompletableDeferred<Unit>()
        val resumeConnection = CompletableDeferred<Unit>()
        repo = FolderRepository(db, grants::persistedReadTrees, grants::releaseRead, { uri, flags ->
            grants.persistRead(uri, flags)
            acquired.complete(Unit)
            resumeConnection.await()
        }) { 77 }
        repo.initialize()
        db.folderDao().upsert(FolderEntity("a", a, "Old", "[]", true, disconnectedAt = 1))
        val other = repo.save(b, "Other", ScanOptions())
        val (_, run) = repo.beginScan(other)
        val connecting = async {
            repo.connect(a, "Reconnected", ScanOptions(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        acquired.await()
        // An already initialized lifecycle call would return immediately without the shared lock.
        val lifecycle = async(start = CoroutineStart.UNDISPATCHED) { repo.initialize() }
        assertFalse(lifecycle.isCompleted)
        val finishing = async(start = CoroutineStart.UNDISPATCHED) { repo.finishScan(run.id, "COMPLETE") }
        assertFalse(finishing.isCompleted)
        resumeConnection.complete(Unit)
        assertEquals("a", connecting.await())
        lifecycle.await()
        assertEquals("COMPLETE", finishing.await()!!.state)
        assertTrue(grants.hasRead(a))
        assertNull(db.folderDao().find("a")!!.disconnectedAt)
    }

    @Test fun save_cannotReconnectThroughSplitGrantAcquisition() = runTest {
        db.folderDao().upsert(FolderEntity("a", a, "Old", "[]", true, disconnectedAt = 1))
        grant(a)
        try {
            repo.save(a, "Unsafe reconnect", ScanOptions())
            fail("Reconnection must acquire its permission inside the shared lifecycle")
        } catch (_: IllegalStateException) { }
        assertEquals(1L, db.folderDao().find("a")!!.disconnectedAt)
        assertEquals("Old", db.folderDao().find("a")!!.displayLocation)
    }

    @Test fun connect_withoutReturnedReadGrantDoesNotReconnectFolder() = runTest {
        db.folderDao().upsert(FolderEntity("a", a, "Old", "[]", true, disconnectedAt = 1))
        try {
            repo.connect(a, "Invalid reconnect", ScanOptions(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            fail("A picker result without READ cannot connect a folder")
        } catch (_: SecurityException) { }
        assertFalse(grants.hasRead(a))
        assertEquals(1L, db.folderDao().find("a")!!.disconnectedAt)
    }

    @Test fun initialize_sweepsOnlyHeldDisconnectedGrantsAndKeepsConnectedEmptyRoot() = runTest {
        grant(a); grant(b)
        val unrelated = "content://repository.test/tree/unrelated"
        grant(unrelated)
        db.folderDao().upsert(FolderEntity("a", a, "A", "[]", true, disconnectedAt = 1))
        db.folderDao().upsert(FolderEntity("b", b, "B", "[]", true))
        repo.initialize()
        assertFalse(grants.hasRead(a))
        assertTrue(grants.hasRead(b)); assertTrue(grants.hasRead(unrelated))
    }
}
