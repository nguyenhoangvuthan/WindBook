package dev.kent.windbook.folders

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelStore
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.folders.*
import dev.kent.windbook.core.model.*
import dev.kent.windbook.testing.MainDispatcherRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val uri = Uri.parse("content://folders.test/tree/root")

    @Test fun returnedReadFlagRequiredAndTypedPathInvalidatesSelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val grants = FolderGrants(context.contentResolver)
        val repo = FolderRepository(db, grants::persistedReadTrees, grants::releaseRead, grants::persistRead)
        val coordinator = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, coordinator, db, context, RepositoryFolderObservations(repo, coordinator))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onTreeGranted(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION).join()
            assertFalse(vm.state.value.canScan)
            assertNotNull(vm.state.value.message)
            vm.onTreeGranted(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            val id = vm.state.value.resolvedFolderId
            assertNotNull(id)
            assertTrue(vm.state.value.canScan)
            assertEquals(ScanOptions(), vm.state.value.options)
            vm.onLocationEdited("/storage/other")
            assertNull(vm.state.value.resolvedFolderId)
            assertFalse(vm.state.value.canScan)
            vm.selectFolder(id!!).join()
            vm.setOptions(ScanOptions(emptySet(), false)).join()
            assertFalse(vm.state.value.canScan)
            vm.disconnect().join()
            assertNotNull(db.folderDao().find(id)!!.disconnectedAt)
        } finally {
            store.clear()
            vm.viewModelScope.coroutineContext[Job]?.join()
            db.close()
        }
    }
    @Test fun scanOutlivesViewModelAndOptionChangesReplaceDurableRun() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val coordinator = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ ->
            flow { awaitCancellation() }
        }), backgroundScope)
        val vm = FolderViewModel(repo, coordinator, db, context, RepositoryFolderObservations(repo, coordinator))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onTreeGranted(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            val id = requireNotNull(vm.state.value.resolvedFolderId)
            vm.scan().join()
            val firstRun = coordinator.observe(id).first { it.state == "RUNNING" }
            vm.setOptions(ScanOptions(setOf(BookFormat.TXT), false)).join()
            val replacement = coordinator.observe(id).first { it.state == "RUNNING" }
            assertNotEquals(firstRun.runId, replacement.runId)
            assertEquals("INTERRUPTED", db.folderDao().run(firstRun.runId)!!.state)
            assertEquals(ScanOptions(setOf(BookFormat.TXT), false), repo.observeFolders().first().single().options)
            store.clear()
            vm.viewModelScope.coroutineContext[Job]?.join()
            assertEquals("RUNNING", coordinator.observe(id).first().state)
            coordinator.stop(replacement.runId)
            assertEquals("INTERRUPTED", coordinator.observe(id).first().state)
        } finally {
            store.clear()
            vm.viewModelScope.coroutineContext[Job]?.join()
            db.close()
        }
    }

    @Test fun rootSwitchClearsOldEntriesAndProgressUntilNewRootEmits() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val a = repo.save(uri.toString(), "A", ScanOptions())
        val b = repo.save("content://folders.test/tree/b", "B", ScanOptions())
        val old = FolderEntry("old-book", "old-source", "old.pdf", "old.pdf", BookFormat.PDF, SourceAvailability.AVAILABLE)
        val lateGate = CompletableDeferred<Unit>()
        val lateDelivered = CompletableDeferred<Unit>()
        val oldEntries = object : kotlinx.coroutines.flow.Flow<List<FolderEntry>> {
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<List<FolderEntry>>) {
                collector.emit(listOf(old))
                try { awaitCancellation() }
                finally { withContext(NonCancellable) {
                    lateGate.await()
                    collector.emit(listOf(old.copy(bookId = "late-book")))
                    lateDelivered.complete(Unit)
                } }
            }
        }
        val inputs = object : FolderObservations {
            override fun entries(folderId: String) = if (folderId == a)
                oldEntries else kotlinx.coroutines.flow.flow<List<FolderEntry>> { awaitCancellation() }
            override fun progress(folderId: String) = if (folderId == a)
                kotlinx.coroutines.flow.flowOf(ScanProgress("old-run", "RUNNING")) else kotlinx.coroutines.flow.flow<ScanProgress> { awaitCancellation() }
        }
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, inputs)
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.selectFolder(a).join()
            vm.state.first { it.entries.isNotEmpty() }
            vm.selectFolder(b).join()
            assertEquals(b, vm.state.value.resolvedFolderId)
            assertEquals(emptyList<FolderEntry>(), vm.state.value.entries)
            assertEquals("IDLE", vm.state.value.progress.state)
            lateGate.complete(Unit)
            lateDelivered.await()
            assertTrue(vm.state.value.entries.isEmpty())
            var opened = false
            vm.openBook(a, "old-book") { opened = true }
            assertFalse(opened)
            vm.disconnect(a).join() // A stale rendered callback must not target the now-selected B.
            assertNull(db.folderDao().find(b)!!.disconnectedAt)
        } finally { lateGate.complete(Unit); store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun rapidExtensionChangesSurviveSuspendedSaveAndQueuedDisconnectCannotTargetNewRoot() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var grantEntered = entered
        var grantRelease = release
        val repo = FolderRepository(db, { emptySet() }, {}, { tree, _ ->
            if (tree.endsWith("/block")) { grantEntered.complete(Unit); grantRelease.await() }
        })
        val a = repo.save(uri.toString(), "A", ScanOptions())
        val b = repo.save("content://folders.test/tree/b", "B", ScanOptions())
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.selectFolder(a).join()
            val blocker = async { repo.connect("content://folders.test/tree/block", "Block", ScanOptions(), 1) }
            entered.await()
            val recursive = vm.setRecursive(false)
            val txt = vm.toggleExtension(BookFormat.TXT)
            val cbz = vm.toggleExtension(BookFormat.CBZ)
            release.complete(Unit)
            blocker.await(); recursive.join(); txt.join(); cbz.join()
            assertEquals(setOf(BookFormat.EPUB, BookFormat.PDF, BookFormat.TXT, BookFormat.CBZ), vm.state.value.options.extensions)
            assertFalse(vm.state.value.options.recursive)
            assertEquals(vm.state.value.options, repo.observeFolders().first().first { it.id == a }.options)
            grantEntered = CompletableDeferred()
            grantRelease = CompletableDeferred()
            val blocker2 = async { repo.connect("content://folders.test/tree/block", "Block", ScanOptions(), 1) }
            grantEntered.await()
            val hold = vm.setRecursive(true)
            // Both requests capture A while save is suspended; disconnect must not target B later.
            val switch = vm.selectFolder(b)
            val staleDisconnect = vm.disconnect()
            grantRelease.complete(Unit)
            blocker2.await(); hold.join(); switch.join(); staleDisconnect.join()
            assertNull(db.folderDao().find(b)!!.disconnectedAt)
        } finally { release.complete(Unit); grantRelease.complete(Unit); store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

}
