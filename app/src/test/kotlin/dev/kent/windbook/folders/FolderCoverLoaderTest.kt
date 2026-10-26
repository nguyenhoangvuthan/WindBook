package dev.kent.windbook.folders

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.folders.*
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.source.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderCoverLoaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private lateinit var folders: FolderRepository
    private lateinit var books: BookSourcesRepository
    private lateinit var files: BookFileStore
    private lateinit var access: SourceAccess
    private lateinit var loader: FolderCoverLoader
    private lateinit var folder: String
    private val tree = "content://cover.test/tree/root"
    private var documents = emptyList<TreeDocument>()
    private val input = mutableMapOf<String, File>()
    private val leases = mutableListOf<SourceLease>()
    private var acquired = 0
    private var extracted = 0
    private var brokenSource: String? = null
    private var brokenMetadata: String? = null
    private var changedProvider: String? = null
    private val stampReads = mutableMapOf<String, Int>()
    private var onExtract: suspend (SourceLease) -> Unit = {}

    @Before fun setup() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        folders = FolderRepository(db, { setOf(tree) }, {}, { _, _ -> })
        books = BookSourcesRepository(db, folders)
        files = BookFileStore(tmp.newFolder("store"))
        access = object : SourceAccess(context) {
            override suspend fun metadata(source: BookSource): SourceStamp {
                val id = (source as BookSource.External).key.documentId
                val reads = (stampReads[id] ?: 0) + 1; stampReads[id] = reads
                return SourceStamp(input.getValue(id).length(), if (id == changedProvider && reads % 2 == 0) 20 else 10)
            }
            override suspend fun acquire(source: BookSource): SourceLease {
                val id = (source as BookSource.External).key.documentId
                if (id == brokenSource) throw IOException("Unavailable")
                acquired++
                return super.acquire(BookSource.Owned(input.getValue(id).path)).also { leases.add(it) }
            }
        }
        val metadata = SourceMetadata { lease, _ ->
            extracted++; onExtract(lease)
            if (lease.file.nameWithoutExtension == brokenMetadata) Result.failure(IOException("Invalid"))
            else Result.success(ExtractedMetadata("Extracted " + lease.file.nameWithoutExtension, "Author", byteArrayOf(5, 6, 7)))
        }
        loader = FolderCoverLoader(books, access, metadata, files)
        folder = folders.save(tree, "Root", ScanOptions(setOf(BookFormat.EPUB, BookFormat.PDF, BookFormat.TXT)))
    }
    @After fun close() = db.close()
    private fun add(id: String, format: BookFormat = BookFormat.EPUB, unknownStamp: Boolean = false) {
        val file = tmp.newFile("$id.${format.extension}").apply { writeText("Distinct source bytes: $id") }
        input[id] = file
        documents = documents + TreeDocument(DocumentKey("cover.test", id), "$tree/document/$id", file.name,
            false, if (unknownStamp) null else file.length(), if (unknownStamp) null else 10)
    }
    private fun coordinator(scope: CoroutineScope) = FolderScanCoordinator(folders,
        FolderScanner(TreeAccess { _, _ -> documents.asFlow() }), scope, loader)
    private suspend fun runScan(c: FolderScanCoordinator): ScanProgress {
        val id = c.start(folder)
        return withContext(Dispatchers.Default) {
            withTimeout(10_000) { c.observe(folder).first { it.runId == id && it.state != "RUNNING" } }
        }
    }
    private suspend fun visible() = db.libraryDao().observeLibrary().first().map { it.book }

    @Test fun scanSavesCoversBeforeAnyBookOpensAndReleasesEveryLease() = runTest {
        add("epub"); add("pdf", BookFormat.PDF); add("text", BookFormat.TXT)
        assertEquals("COMPLETE", runScan(coordinator(backgroundScope)).state)
        val rows = visible(); assertEquals(3, rows.size)
        assertEquals(2, rows.count { it.coverPath?.let { path -> File(path).readBytes().contentEquals(byteArrayOf(5, 6, 7)) } == true })
        assertTrue(rows.all { it.lastOpenedAt == null && it.status == "TO_READ" })
        assertEquals(2, acquired); assertEquals(2, extracted)
        leases.forEach { lease -> assertThrows(IllegalStateException::class.java) { lease.openStream() } }
    }
    @Test fun unchangedValidCoverIsReusedOnRescan() = runTest {
        add("one"); val c = coordinator(backgroundScope)
        runScan(c); val cover = visible().single().coverPath
        runScan(c)
        assertEquals(1, acquired); assertEquals(1, extracted); assertEquals(cover, visible().single().coverPath)
    }
    @Test fun missingCoverFileIsPreparedAgainAndUsesNewCachePath() = runTest {
        add("one"); val c = coordinator(backgroundScope); runScan(c)
        val old = visible().single().coverPath!!; File(old).delete()
        runScan(c)
        assertEquals(2, acquired); assertNotEquals(old, visible().single().coverPath)
        assertTrue(File(visible().single().coverPath!!).isFile)
    }
    @Test fun unknownStampsCannotProveCachedSourceUnchanged() = runTest {
        add("one", unknownStamp = true); val c = coordinator(backgroundScope)
        runScan(c); val first = visible().single().coverPath!!
        runScan(c); runScan(c); assertEquals(3, acquired)
        assertFalse(File(first).exists())
        assertEquals(1, files.coversDir.listFiles().orEmpty().size)
    }
    @Test fun changingBytesPreservesCoverReferencedByOldRevision() = runTest {
        add("one", unknownStamp = true); val c = coordinator(backgroundScope); runScan(c)
        val old = visible().single(); val oldCover = old.coverPath!!
        db.libraryDao().upsertProgress(ReadingProgressEntity(old.id, "old-anchor", .4, 1))
        input.getValue("one").appendText("new revision bytes")
        runScan(c)
        val rows = visible()
        assertEquals(2, rows.size)
        assertTrue(File(oldCover).isFile)
        assertTrue(rows.all { File(it.coverPath!!).isFile })
        assertEquals(2, files.coversDir.listFiles().orEmpty().size)
        assertEquals("old-anchor", db.libraryDao().progress(old.id)!!.locatorJson)
    }

    @Test fun replacingCoverRetainsAnImageStillReferencedByHiddenBook() = runTest {
        add("one", unknownStamp = true); val c = coordinator(backgroundScope); runScan(c)
        val old = visible().single(); val cover = old.coverPath!!
        db.libraryDao().insert(old.copy(id = "historical", deletedAt = 1))
        runScan(c)
        assertTrue(File(cover).isFile)
        assertEquals(2, files.coversDir.listFiles().orEmpty().size)
    }

    @Test fun badMetadataAndUnavailableSourceDoNotPreventOtherCovers() = runTest {
        add("bad"); add("denied"); add("good", BookFormat.PDF)
        brokenMetadata = "bad"; brokenSource = "denied"
        assertEquals("COMPLETE", runScan(coordinator(backgroundScope)).state)
        assertEquals(1, visible().count { it.coverPath != null })
        leases.forEach { lease -> assertThrows(IllegalStateException::class.java) { lease.openStream() } }
    }
    @Test fun providerChangingDuringMetadataCannotPublishItsCover() = runTest {
        add("changed"); changedProvider = "changed"
        runScan(coordinator(backgroundScope))
        assertNull(visible().single().coverPath); assertTrue(files.coversDir.listFiles().orEmpty().isEmpty())
    }
    @Test fun hidingDuringExtractionDiscardsStagedCover() = runTest {
        add("one")
        onExtract = { db.libraryDao().tombstone(db.sourceDao().findDocument("cover.test", "one")!!.bookId, 1) }
        runScan(coordinator(backgroundScope))
        assertTrue(visible().isEmpty()); assertTrue(files.coversDir.listFiles().orEmpty().isEmpty())
    }
    @Test fun cancelledNativeResultDoesNotPublishAndStillClosesLease() = runTest {
        add("one")
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        onExtract = { entered.complete(Unit); withContext(NonCancellable) { release.await() } }
        val c = coordinator(backgroundScope); val id = c.start(folder)
        withContext(Dispatchers.Default) { withTimeout(10_000) { entered.await() } }
        val stopping = async { c.stop(id) }
        withContext(Dispatchers.Default) { withTimeout(10_000) { c.observe(folder).first { it.state == "INTERRUPTED" } } }
        release.complete(Unit); stopping.await()
        assertNull(visible().single().coverPath); assertTrue(files.coversDir.listFiles().orEmpty().isEmpty())
        assertThrows(IllegalStateException::class.java) { leases.single().openStream() }
    }
}
