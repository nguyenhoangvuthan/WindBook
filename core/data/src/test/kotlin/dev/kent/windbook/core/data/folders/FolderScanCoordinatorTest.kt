package dev.kent.windbook.core.data.folders

import android.content.Context
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import java.io.File
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
class FolderScanCoordinatorTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: WindBookDatabase
    private lateinit var repo: FolderRepository
    private val tree = "content://scan.test/tree/root"
    private val root = DocumentKey("scan.test", "root")
    private var children: (String, DocumentKey) -> Flow<TreeDocument> = { _, _ -> emptyFlow() }
    private val access = TreeAccess { uri, key -> children(uri, key) }
    @Before fun setup() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> error("No picker acquisition expected") }) { 99 }
    }
    @After fun close() = db.close()
    private fun doc(id: String, name: String = "$id.pdf", directory: Boolean = false, uri: String = tree) =
        TreeDocument(DocumentKey("scan.test", id), "$uri/document/$id", name, directory, null, null)
    private suspend fun terminal(coordinator: FolderScanCoordinator, folder: String, id: String) =
        withContext(Dispatchers.Default) {
            withTimeout(10_000) { coordinator.observe(folder).first { it.runId == id && it.state != "RUNNING" } }
        }

    @Test fun repeatedAndOverlappingScans_reuseIdentityKeepHiddenAndFilterObservedEntries() = runTest {
        // A usable canonical grant remains preferred across overlapping discovery.
        repo = FolderRepository(db, { setOf(tree, "content://scan.test/tree/other") }, {}, { _, _ -> }) { 99 }
        val coordinator = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        val folder = repo.save(tree, "Root", ScanOptions())
        children = { uri, _ -> flowOf(doc("one", "Alpha.pdf", uri = uri), doc("two", "Beta.EPUB", uri = uri)) }
        val first = terminal(coordinator, folder, coordinator.start(folder))
        assertEquals(2L, first.added)
        val book = repo.observeEntries(folder, "Alpha", emptySet()).first().single().bookId
        db.libraryDao().hideExternal(book, 2)
        val second = terminal(coordinator, folder, coordinator.start(folder))
        assertEquals(0L, second.added); assertEquals(2L, second.known)
        assertEquals(2, db.libraryDao().allIdsIncludingHidden().size)
        assertNull(db.libraryDao().findById(book))
        assertEquals(listOf("Beta.EPUB"), repo.observeEntries(folder, "bEtA", setOf(BookFormat.EPUB)).first().map { it.name })
        assertTrue(repo.observeEntries(folder, "", setOf(BookFormat.PDF)).first().isEmpty())
        val other = repo.save("content://scan.test/tree/other", "Other", ScanOptions())
        terminal(coordinator, other, coordinator.start(other))
        assertEquals(2, db.libraryDao().allIdsIncludingHidden().size)
        assertEquals(2, db.folderDao().memberships(db.sourceDao().forBook(book).single().id).size)
        assertEquals("$tree/document/one", db.sourceDao().forBook(book).single().uri)
        assertEquals(99L, repo.observeFolders().first().first { it.id == folder }.lastScanAt)
    }

    @Test fun successfulScan_marksOnlySelectedCoverageMissingAndPreservesOtherRoots() = runTest {
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        val folder = repo.save(tree, "Root", ScanOptions())
        children = { _, key -> if (key == root) flowOf(doc("pdf"), doc("epub", "book.epub"), doc("sub", "sub", true)) else flowOf(doc("nested")) }
        terminal(c, folder, c.start(folder))
        val other = repo.save("content://scan.test/tree/other", "Other", ScanOptions())
        children = { uri, _ -> flowOf(doc("pdf", uri = uri)) }
        terminal(c, other, c.start(other))
        repo.save(tree, "Root", ScanOptions(setOf(BookFormat.PDF), false))
        children = { _, _ -> emptyFlow() }
        terminal(c, folder, c.start(folder))
        val entries = repo.observeEntries(folder, "", emptySet()).first().associateBy { it.name }
        assertEquals(SourceAvailability.UNVERIFIED, entries.getValue("pdf.pdf").availability)
        assertEquals(SourceAvailability.UNVERIFIED, entries.getValue("book.epub").availability)
        assertEquals(SourceAvailability.UNVERIFIED, entries.getValue("nested.pdf").availability)
        terminal(c, other, c.start(other))
        assertEquals(SourceAvailability.MISSING, repo.observeEntries(folder, "pdf.pdf", emptySet()).first().single().availability)
        assertEquals(3, db.libraryDao().allIdsIncludingHidden().size)
    }

    @Test fun failedAndCancelledScans_neverMarkUnseenMissingAndKeepCommittedBatches() = runTest {
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        val folder = repo.save(tree, "Root", ScanOptions())
        children = { _, _ -> flowOf(doc("old")) }
        terminal(c, folder, c.start(folder))
        children = { _, _ -> flow { emit(doc("new")); throw SecurityException("denied") } }
        val failed = terminal(c, folder, c.start(folder))
        assertEquals("INCOMPLETE", failed.state); assertEquals(1L, failed.directoryErrors)
        assertTrue(repo.observeEntries(folder, "", emptySet()).first().none { it.availability == SourceAvailability.MISSING })
        val committed = CompletableDeferred<Unit>()
        children = { _, _ -> flow {
            repeat(201) { emit(doc("batch$it")) }
            committed.complete(Unit)
            awaitCancellation()
        } }
        val run = c.start(folder)
        committed.await()
        c.stop(run)
        val stopped = terminal(c, folder, run)
        assertEquals("INTERRUPTED", stopped.state)
        assertTrue(stopped.added >= 100)
        assertTrue(repo.observeEntries(folder, "", emptySet()).first().none { it.availability == SourceAvailability.MISSING })
        assertTrue(db.libraryDao().allIdsIncludingHidden().size >= 102)
    }

    @Test fun concurrentStarts_andLateCancelledEmissionCannotOverwriteNewRun() = runTest {
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        val folder = repo.save(tree, "Root", ScanOptions())
        val entered = CompletableDeferred<Unit>()
        val late = CompletableDeferred<Unit>()
        children = { _, _ -> flow {
            entered.complete(Unit)
            try { awaitCancellation() } finally {
                withContext(NonCancellable) {
                    try { emit(doc("late")) } catch (_: Exception) { }
                    late.complete(Unit)
                }
            }
        } }
        val old = c.start(folder)
        entered.await()
        children = { _, _ -> emptyFlow() }
        val starts = listOf(async { c.start(folder) }, async { c.start(folder) }).awaitAll()
        late.await()
        assertEquals("INTERRUPTED", db.folderDao().run(old)!!.state)
        val latest = c.observe(folder).first()
        assertTrue(latest.runId in starts)
        terminal(c, folder, latest.runId)
        assertFalse(db.libraryDao().allIdsIncludingHidden().isNotEmpty())
        assertEquals(latest.runId, c.observe(folder).first().runId)
        c.stop(old)
        assertEquals(latest.runId, c.observe(folder).first().runId)
    }

    @Test fun unnamedRows_doNotCreateCandidatesOrMarkKnownDocumentMissing() = runTest {
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        val folder = repo.save(tree, "Root", ScanOptions())
        children = { _, _ -> flowOf(doc("known")) }
        terminal(c, folder, c.start(folder))
        children = { _, _ -> flowOf(doc("known", "")) }
        val result = terminal(c, folder, c.start(folder))
        assertEquals(1L, result.examined); assertEquals(1L, result.unreadable)
        assertEquals("INCOMPLETE", result.state)
        assertEquals(1, db.libraryDao().allIdsIncludingHidden().size)
        assertEquals(SourceAvailability.UNVERIFIED, repo.observeEntries(folder, "", emptySet()).first().single().availability)
    }

    @Test fun stopAsFirstOperation_interruptsAllStaleRunsBeforeReturning() = runTest {
        db.folderDao().upsert(FolderEntity("f", tree, "Root", "[\"PDF\"]", true))
        db.folderDao().upsertRun(ScanRunEntity("one", "f", "{}", "RUNNING"))
        db.folderDao().upsertRun(ScanRunEntity("two", "f", "{}", "RUNNING"))
        FolderScanCoordinator(repo, FolderScanner(access), backgroundScope).stop("one")
        assertEquals("INTERRUPTED", db.folderDao().run("one")!!.state)
        assertEquals("INTERRUPTED", db.folderDao().run("two")!!.state)
    }

    @Test fun writeAsFirstOperation_rejectsOldProcessTokenAndNeverInsertsLateSource() = runTest {
        db.folderDao().upsert(FolderEntity("f", tree, "Root", "[\"PDF\"]", true))
        db.folderDao().upsertRun(ScanRunEntity("old", "f", "{}", "RUNNING"))
        val result = repo.writeBatch("old", listOf(ScanEvent.Found(doc("late"), "late.pdf", root)),
            1, 0, 0, "late.pdf")
        assertNull(result)
        assertTrue(db.libraryDao().allIdsIncludingHidden().isEmpty())
        assertEquals("INTERRUPTED", db.folderDao().run("old")!!.state)
    }

    @Test fun staleWriterAndCompletion_cannotMutateNewRunOrItsLatestSummary() = runTest {
        val folder = repo.save(tree, "Root", ScanOptions())
        val (_, old) = repo.beginScan(folder)
        val (_, current) = repo.beginScan(folder)
        assertNull(repo.writeBatch(old.id, listOf(ScanEvent.Found(doc("late"), "late.pdf", root)),
            1, 0, 0, "late.pdf"))
        repo.writeBatch(current.id, listOf(ScanEvent.Found(doc("current"), "current.pdf", root)),
            1, 0, 0, "current.pdf")
        repo.finishScan(current.id, "COMPLETE")
        repo.finishScan(old.id, "COMPLETE")
        assertEquals("INTERRUPTED", db.folderDao().run(old.id)!!.state)
        val result = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope).observe(folder).first()
        assertEquals(current.id, result.runId); assertEquals("COMPLETE", result.state)
        assertEquals(1L, result.added); assertEquals("current.pdf", result.currentPath)
        assertEquals(listOf("current.pdf"), repo.observeEntries(folder, "", emptySet()).first().map { it.name })
        assertEquals(99L, repo.observeFolders().first().single().lastScanAt)
    }

    @Test fun batches_rejectMoreThan100EntriesBeforeAnyMutation() = runTest {
        val folder = repo.save(tree, "Root", ScanOptions())
        val (_, run) = repo.beginScan(folder)
        val entries = (0..100).map { ScanEvent.Found(doc("bounded$it"), "bounded$it.pdf", root) }
        try {
            repo.writeBatch(run.id, entries, 101, 0, 0, "bounded100.pdf")
            fail("An oversized transaction must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertTrue(db.libraryDao().allIdsIncludingHidden().isEmpty())
        assertEquals(0L, db.folderDao().run(run.id)!!.examined)
        val result = repo.writeBatch(run.id, entries.take(100), 100, 0, 0, "bounded99.pdf")!!
        assertEquals(100L, result.added)
        assertEquals(100, db.libraryDao().allIdsIncludingHidden().size)
        repo.finishScan(run.id, "INTERRUPTED")
    }

    @Test fun switchingRoots_commitsInterruptedRunAndClosesOldCollectorBeforeEnumeratingNewRoot() = runTest {
        val otherTree = "content://scan.test/tree/other"
        val folder = repo.save(tree, "Root", ScanOptions())
        val other = repo.save(otherTree, "Other", ScanOptions())
        val entered = CompletableDeferred<Unit>()
        var oldCollectorClosed = false
        var oldId = ""
        children = { uri, _ -> flow {
            if (uri == tree) {
                entered.complete(Unit)
                try { awaitCancellation() } finally { oldCollectorClosed = true }
            } else {
                assertTrue(oldCollectorClosed)
                assertEquals("INTERRUPTED", db.folderDao().run(oldId)!!.state)
                emit(doc("new", uri = uri))
            }
        } }
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        oldId = c.start(folder)
        entered.await()
        val result = terminal(c, other, c.start(other))
        assertEquals("COMPLETE", result.state); assertEquals(1L, result.added)
    }

    @Test fun overlappingNewRoot_repairsRevokedCanonicalAccessWithoutChangingReadingIdentity() = runTest {
        val otherTree = "content://scan.test/tree/other"
        val held = mutableSetOf(tree)
        repo = FolderRepository(db, { held.toSet() }, { held.remove(it) }, { _, _ -> }) { 99 }
        val folder = repo.save(tree, "Root", ScanOptions())
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        children = { uri, _ -> flowOf(doc("shared", uri = uri)) }
        terminal(c, folder, c.start(folder))
        val before = db.sourceDao().findDocument("scan.test", "shared")!!
        db.libraryDao().upsertProgress(ReadingProgressEntity(before.bookId, "anchor", .5, 1))
        held.remove(tree); held.add(otherTree)
        val other = repo.save(otherTree, "New grant", ScanOptions())
        terminal(c, other, c.start(other))
        val after = db.sourceDao().findDocument("scan.test", "shared")!!
        assertEquals(before.id, after.id)
        assertEquals(before.bookId, after.bookId)
        assertEquals("$otherTree/document/shared", after.uri)
        assertEquals(2, db.folderDao().memberships(after.id).size)
        assertEquals("anchor", db.libraryDao().progress(after.bookId)!!.locatorJson)
    }

    @Test fun successfulReconciliation_retainsCanonicalGrantUntilLastConnectedFolderDisconnects() = runTest {
        val otherTree = "content://scan.test/tree/other"
        val held = mutableSetOf(tree, otherTree)
        repo = FolderRepository(db, { held.toSet() }, { held.remove(it) }, { _, _ -> error("No picker acquisition expected") }) { 99 }
        val folder = repo.save(tree, "Root", ScanOptions())
        val other = repo.save(otherTree, "Other", ScanOptions())
        val c = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope)
        children = { uri, _ -> flowOf(doc("shared", uri = uri)) }
        terminal(c, folder, c.start(folder))
        terminal(c, other, c.start(other))
        repo.disconnectFolder(folder)
        assertEquals(setOf(tree, otherTree), held)
        children = { _, _ -> emptyFlow() }
        val result = terminal(c, other, c.start(other))
        // The DB Flow can emit before post-commit permission release finishes; join the job.
        c.stop(result.runId)
        assertEquals(setOf(tree, otherTree), held)
        assertEquals(SourceAvailability.MISSING, repo.observeEntries(other, "", emptySet()).first().single().availability)
        repo.disconnectFolder(other)
        assertTrue(held.isEmpty())
    }

    @Test fun restart_interruptsStaleRunsAndRestoresLatestSummaryWhenTimestampsTie() = runTest {
        db.close()
        val path = File(tmp.root, "restart.db").absolutePath
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = WindBookDatabase.open(context, path, AndroidSQLiteDriver())
        repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> error("No picker acquisition expected") }) { 99 }
        val folder = repo.save(tree, "Root", ScanOptions())
        db.folderDao().upsertRun(ScanRunEntity("zzz", folder, "{}", "COMPLETE", added = 3, startedAt = 99))
        db.folderDao().upsertRun(ScanRunEntity("aaa", folder, "{}", "RUNNING", examined = 7, currentPath = "child", startedAt = 99))
        db.close()
        db = WindBookDatabase.open(context, path, AndroidSQLiteDriver())
        repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> error("No picker acquisition expected") }) { 100 }
        repo.initialize()
        val result = FolderScanCoordinator(repo, FolderScanner(access), backgroundScope).observe(folder).first()
        assertEquals("aaa", result.runId); assertEquals("INTERRUPTED", result.state)
        assertEquals(7L, result.examined); assertEquals("child", result.currentPath)
        assertEquals(3L, db.folderDao().run("zzz")!!.added)
    }
}
