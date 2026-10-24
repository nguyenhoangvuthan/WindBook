package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookSourcesRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repository: BookSourcesRepository
    private val source = BookSource.External("content://test/document/a", DocumentKey("test", "a"))
    @Before fun setup() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        repository = BookSourcesRepository(db, FolderRepository(db, { emptySet() }, {}, { _, _ -> })) { 77 }
    }
    @After fun close() = db.close()
    private suspend fun book(id: String, sha: String? = null, hidden: Boolean = false) {
        db.libraryDao().insert(BookEntity(id, "$id.epub", null, "EPUB", null, 12, sha, null, null,
            "TO_READ", 1, null, 1, if (hidden) 2 else null))
    }
    private suspend fun addSource(id: String = "a", value: BookSource.External = source, sha: String? = null) {
        db.sourceDao().insert(BookSourceEntity("source-$id", id, "EXTERNAL", uri = value.uri,
            authority = value.key.authority, documentId = value.key.documentId, size = 12, mtime = 100, verifiedSha256 = sha))
    }
    @Test fun textEncodingHistoryIsSharedAcrossSourcesAndMirrorsActiveProgress() = runTest {
        book("a", "same"); addSource(sha = "same")
        val other = BookSource.External("content://test/document/b", DocumentKey("test", "b"))
        repository.reconnect("a", other, "same")
        repository.setTextEncoding("a", source, "UTF8")
        repository.activateTextEncoding("a", "UTF8")
        repository.saveTextProgress("a", "UTF8", "first", .3)
        assertEquals("first", repository.activateTextEncoding("a", "UTF8"))
        repository.setTextEncoding("a", other, "WINDOWS_1258")
        assertEquals("UTF8", repository.textEncoding("a", source))
        assertNull(repository.activateTextEncoding("a", "WINDOWS_1258"))
        assertNull(db.libraryDao().progress("a"))
        repository.saveTextProgress("a", "UTF8", "late-old-session", .4)
        assertNull(db.libraryDao().progress("a"))
        repository.saveTextProgress("a", "WINDOWS_1258", "second", .7)
        assertEquals("second", db.libraryDao().progress("a")!!.locatorJson)
        assertEquals("late-old-session", repository.activateTextEncoding("a", "UTF8"))
        assertEquals("late-old-session", db.libraryDao().progress("a")!!.locatorJson)
    }
    @Test fun textHistoryPreventsUnreadDuplicateRedirect() = runTest {
        book("canonical", "same"); book("a"); addSource()
        repository.activateTextEncoding("a", "UTF8")
        repository.saveTextProgress("a", "UTF8", "anchor", .2)
        repository.activateTextEncoding("a", "WINDOWS_1258")
        assertNull(db.libraryDao().progress("a"))
        assertEquals(Verification.Ready("a", source), repository.verify("a", source, "same"))
        assertEquals("anchor", repository.activateTextEncoding("a", "UTF8"))
    }
    @Test fun unchangedMetadataWithDifferentBytes_keepsOldProgressAndCreatesNewRevision() = runTest {
        book("a", "old"); addSource(sha = "old")
        db.libraryDao().upsertProgress(ReadingProgressEntity("a", "old-locator", .4, 1))
        val changed = repository.verify("a", source, "new") as Verification.Changed
        assertNotEquals("a", changed.newBookId)
        assertEquals("old-locator", db.libraryDao().progress("a")!!.locatorJson)
        assertNull(db.libraryDao().progress(changed.newBookId))
        assertEquals("old", db.libraryDao().findById("a")!!.sha256)
        assertEquals(listOf(source), repository.sources(changed.newBookId))
    }
    @Test fun verifiedDuplicate_redirectsUnreadCandidateAndRetainsAllSources() = runTest {
        book("canonical", "same"); book("a"); addSource()
        val result = repository.verify("a", source, "same")
        assertEquals(Verification.Redirect("canonical"), result)
        assertEquals(listOf(source), repository.sources("canonical"))
        assertNotNull(db.libraryDao().findIncludingHidden("a"))
        assertNull(db.libraryDao().findById("a"))
    }
    @Test fun readDuplicate_keepsSeparateIdentityAndReadingData() = runTest {
        book("canonical", "same"); book("a"); addSource()
        db.libraryDao().upsertProgress(ReadingProgressEntity("a", "anchor", .5, 1))
        assertEquals(Verification.Ready("a", source), repository.verify("a", source, "same"))
        assertEquals("anchor", db.libraryDao().progress("a")!!.locatorJson)
        assertEquals("same", db.sourceDao().forBook("a").single().verifiedSha256)
    }
    @Test fun preferenceBearingDuplicate_isRetained() = runTest {
        book("canonical", "same"); book("a"); addSource()
        db.bookPreferencesDao().upsert(BookPreferencesEntity("a", "{\"unknown\":1}", 1))
        assertEquals(Verification.Ready("a", source), repository.verify("a", source, "same"))
        assertEquals("{\"unknown\":1}", db.bookPreferencesDao().json("a"))
    }
    @Test fun hiddenDuplicate_requiresExplicitRestoreBeforeRelinking() = runTest {
        book("hidden", "same", hidden = true); book("a"); addSource()
        assertEquals(Verification.HiddenMatch("hidden"), repository.verify("a", source, "same"))
        assertEquals(listOf(source), repository.sources("a"))
        assertNull(db.libraryDao().findById("hidden"))
        repository.restoreExternalBook("hidden")
        assertEquals(Verification.Redirect("hidden"), repository.verify("a", source, "same"))
    }
    @Test fun reconnectSameBytes_preservesIdentityProgressAndExistingSource() = runTest {
        book("a", "same"); addSource(sha = "same")
        db.libraryDao().upsertProgress(ReadingProgressEntity("a", "anchor", .5, 1))
        val replacement = BookSource.External("content://test/document/b", DocumentKey("test", "b"))
        assertEquals(Verification.Ready("a", replacement), repository.reconnect("a", replacement, "same"))
        assertEquals(setOf(source, replacement), repository.sources("a").toSet())
        assertEquals("anchor", db.libraryDao().progress("a")!!.locatorJson)
    }
    @Test fun reconnectDifferentBytes_requiresNewRevisionAndRetainsOriginal() = runTest {
        book("a", "old"); addSource(sha = "old")
        val replacement = BookSource.External("content://test/document/b", DocumentKey("test", "b"))
        val changed = repository.reconnect("a", replacement, "new") as Verification.Changed
        assertEquals(listOf(source), repository.sources("a"))
        assertEquals(listOf(replacement), repository.sources(changed.newBookId))
    }
    @Test fun readConflictThenReplacementThenOldBytesReconnect_recoversOriginalIdentity() = runTest {
        book("canonical", "old"); book("a"); addSource()
        db.libraryDao().upsertProgress(ReadingProgressEntity("a", "anchor", .5, 1))
        repository.verify("a", source, "old")
        repository.verify("a", source, "new")
        assertEquals("old", db.libraryDao().findById("a")!!.sha256)
        val replacement = BookSource.External("content://test/document/b", DocumentKey("test", "b"))
        assertEquals(Verification.Ready("a", replacement), repository.reconnect("a", replacement, "old"))
        assertEquals("anchor", db.libraryDao().progress("a")!!.locatorJson)
    }
    @Test fun visibleMatchTakesPriorityOverHiddenOwnedMatch() = runTest {
        book("hidden", "same", hidden = true); book("visible", "same"); book("a"); addSource()
        db.libraryDao().update(db.libraryDao().findIncludingHidden("hidden")!!.copy(filePath = "/owned"))
        assertEquals(Verification.Redirect("visible"), repository.verify("a", source, "same"))
    }
    @Test fun absentSizeAndMtime_doesNotPreventFingerprintVerification() = runTest {
        book("a"); addSource()
        db.sourceDao().update(db.sourceDao().forBook("a").single().copy(size = null, mtime = null))
        assertEquals(Verification.Ready("a", source), repository.verify("a", source, "same"))
        assertEquals("same", db.libraryDao().findById("a")!!.sha256)
    }
}
