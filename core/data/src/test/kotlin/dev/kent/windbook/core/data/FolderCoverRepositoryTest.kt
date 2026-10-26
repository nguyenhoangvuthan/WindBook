package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.folders.*
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderCoverRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var folders: FolderRepository
    private lateinit var books: BookSourcesRepository
    private lateinit var folder: String
    private lateinit var run: ScanRunEntity
    private val tree = "content://covers.test/tree/root"
    @Before fun setup() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        folders = FolderRepository(db, { setOf(tree) }, {}, { _, _ -> }) { 100 }
        books = BookSourcesRepository(db, folders) { 200 }
        folder = folders.save(tree, "Test", ScanOptions())
        run = folders.beginScan(folder).second
    }
    @After fun close() = db.close()
    private suspend fun add(id: String = "one", format: BookFormat = BookFormat.EPUB, modified: Long = 10): FolderCoverCandidate {
        val found = ScanEvent.Found(TreeDocument(DocumentKey("covers.test", id), "$tree/document/$id",
            "$id.${format.extension}", false, 12, modified), "$id.${format.extension}", DocumentKey("covers.test", "root"))
        folders.writeBatch(run.id, listOf(found), 1, 0, 0, found.relativePath)
        val source = db.sourceDao().findDocument("covers.test", id)!!
        val book = db.libraryDao().findById(source.bookId)!!
        return FolderCoverCandidate(run.id, source, format, book.sha256, book.coverPath, found.relativePath)
    }
    private val metadata = ExtractedMetadata("Actual title", "Author", byteArrayOf(1, 2))

    @Test fun candidatesAreBoundedOrderedVisibleEpubPdfFromActiveRunOnly() = runTest {
        val a = add("a"); val b = add("b", BookFormat.PDF); add("txt", BookFormat.TXT)
        val hidden = add("hidden"); db.libraryDao().tombstone(hidden.source.bookId, 1)
        val all = books.folderCoverCandidates(run.id)
        assertEquals(listOf(a, b).sortedBy { it.source.id }, all)
        val first = books.folderCoverCandidates(run.id, limit = 1).single()
        assertEquals(all.drop(1), books.folderCoverCandidates(run.id, first.source.id, 1))
        folders.finishScan(run.id, "INTERRUPTED")
        assertTrue(books.folderCoverCandidates(run.id).isEmpty())
    }
    @Test fun publishesCoverAndMetadataWithoutOpeningOrChangingProgress() = runTest {
        val candidate = add()
        assertTrue(books.commitFolderMetadata(candidate, "sha", metadata, "/covers/unique.webp").accepted)
        val row = db.libraryDao().findById(candidate.source.bookId)!!
        assertEquals("Actual title", row.title); assertEquals("Author", row.author)
        assertEquals("/covers/unique.webp", row.coverPath); assertEquals("sha", row.sha256)
        assertNull(row.lastOpenedAt); assertNull(db.libraryDao().progress(row.id)); assertEquals("TO_READ", row.status)
    }
    @Test fun stoppedRunCannotPublishLateNativeResult() = runTest {
        val candidate = add(); folders.finishScan(run.id, "INTERRUPTED")
        assertFalse(books.commitFolderMetadata(candidate, "sha", metadata, "/late.webp").accepted)
        assertNull(db.libraryDao().findById(candidate.source.bookId)!!.coverPath)
    }
    @Test fun disconnectedFolderCannotPublishLateResult() = runTest {
        val candidate = add(); folders.disconnectFolder(folder)
        assertFalse(books.commitFolderMetadata(candidate, "sha", metadata, "/late.webp").accepted)
    }
    @Test fun hiddenBookIsNotRestoredByCoverPreparation() = runTest {
        val candidate = add(); db.libraryDao().tombstone(candidate.source.bookId, 1)
        assertFalse(books.commitFolderMetadata(candidate, "sha", metadata, "/late.webp").accepted)
        assertNull(db.libraryDao().findById(candidate.source.bookId))
    }
    @Test fun sourceOrCoverChangedByReaderRejectsStaleBackgroundPublication() = runTest {
        val candidate = add()
        books.enrich(candidate.source.bookId, ExtractedMetadata("Live title", null, null), "/reader.webp")
        assertFalse(books.commitFolderMetadata(candidate, "sha", metadata, "/late.webp").accepted)
        assertEquals("/reader.webp", db.libraryDao().findById(candidate.source.bookId)!!.coverPath)
        val fresh = books.folderCoverCandidates(run.id).single()
        db.sourceDao().update(fresh.source.copy(uri = "$tree/document/replacement"))
        assertFalse(books.commitFolderMetadata(fresh, "sha", metadata, "/late.webp").accepted)
    }
    @Test fun changedBytesKeepOldCoverAndProgressOnOldRevision() = runTest {
        val candidate = add()
        val old = db.libraryDao().findById(candidate.source.bookId)!!
        db.libraryDao().update(old.copy(sha256 = "old", coverPath = "/old.webp"))
        db.sourceDao().update(candidate.source.copy(verifiedSha256 = "old"))
        db.libraryDao().upsertProgress(ReadingProgressEntity(old.id, "old-anchor", .6, 1))
        val fresh = books.folderCoverCandidates(run.id).single()
        assertTrue(books.commitFolderMetadata(fresh, "new", metadata, "/new.webp").accepted)
        assertEquals("old-anchor", db.libraryDao().progress(old.id)!!.locatorJson)
        assertEquals("/old.webp", db.libraryDao().findById(old.id)!!.coverPath)
        val current = db.sourceDao().findDocument("covers.test", "one")!!
        assertNotEquals(old.id, current.bookId)
        assertEquals("/new.webp", db.libraryDao().findById(current.bookId)!!.coverPath)
        assertNull(db.libraryDao().progress(current.bookId))
    }
    @Test fun backgroundDuplicateKeepsIdUntilReaderPerformsRedirect() = runTest {
        val candidate = add()
        db.libraryDao().insert(BookEntity("canonical", "Existing", null, "EPUB", "/owned.epub", 12,
            "sha", null, null, "TO_READ", 0, null, 0, null))
        assertTrue(books.commitFolderMetadata(candidate, "sha", metadata, "/new.webp").accepted)
        assertNotNull(db.libraryDao().findById(candidate.source.bookId))
        assertEquals(candidate.source.bookId, db.sourceDao().findDocument("covers.test", "one")!!.bookId)
        assertEquals("/new.webp", db.libraryDao().findById(candidate.source.bookId)!!.coverPath)
        val capturedSource = BookSource.External(candidate.source.uri!!, DocumentKey("covers.test", "one"))
        assertEquals(Verification.Redirect("canonical"), books.verify(candidate.source.bookId, capturedSource, "sha"))
        assertNull(db.libraryDao().findById("canonical")!!.lastOpenedAt)
    }
    @Test fun hiddenCanonicalMatchIsNotRevivedOrGivenNewCover() = runTest {
        val candidate = add()
        db.libraryDao().insert(BookEntity("hidden", "Hidden", null, "EPUB", null, 12,
            "sha", null, null, "TO_READ", 0, null, 0, 1))
        assertFalse(books.commitFolderMetadata(candidate, "sha", metadata, "/new.webp").accepted)
        assertNull(db.libraryDao().findById("hidden")); assertNull(db.libraryDao().findById(candidate.source.bookId)!!.coverPath)
    }
    @Test fun rescanChangedStampInvalidatesCachedCoverIdentity() = runTest {
        val candidate = add()
        books.verify(candidate.source.bookId, BookSource.External(candidate.source.uri!!, DocumentKey("covers.test", "one")), "old")
        assertEquals("AVAILABLE", db.sourceDao().findDocument("covers.test", "one")!!.availability)
        run = folders.beginScan(folder).second
        val fresh = add(modified = 20)
        assertEquals("UNVERIFIED", fresh.source.availability)
        assertEquals("old", fresh.source.verifiedSha256)
    }
}
