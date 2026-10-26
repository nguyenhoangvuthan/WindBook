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
    private val unusedOpener = dev.kent.windbook.reader.BookOpener { _, _ -> error("Folder actions do not open sessions") }
    private fun preferences(scope: CoroutineScope): dev.kent.windbook.core.data.LibraryPreferences =
        dev.kent.windbook.core.data.LibraryPreferences(androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) {
            java.io.File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "scan-${java.util.UUID.randomUUID()}.preferences_pb")
        })
    private suspend fun grant(vm: FolderViewModel, uri: Uri, flags: Int): Job {
        vm.requestFolder().join()
        return vm.onTreeGranted(uri, flags, requireNotNull(vm.state.value.pickerRequest).id)
    }
    private val uri = Uri.parse("content://folders.test/tree/root")

    @Test fun returnedReadFlagRequiredAndTypedPathInvalidatesSelection() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val grants = FolderGrants(context.contentResolver)
        val repo = FolderRepository(db, grants::persistedReadTrees, grants::releaseRead, grants::persistRead)
        val coordinator = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, coordinator, db, context, RepositoryFolderObservations(repo, coordinator), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            grant(vm, uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION).join()
            assertFalse(vm.state.value.canScan)
            assertNotNull(vm.state.value.message)
            grant(vm, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            val id = vm.state.value.resolvedFolderId
            assertNotNull(id)
            assertTrue(vm.state.value.canScan)
            assertEquals(ScanOptions(), vm.state.value.options)
            vm.onLocationEdited("/storage/other")
            assertNull(vm.state.value.resolvedFolderId)
            assertTrue(vm.state.value.canScan)
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
    @Test fun grantedFolderStartsScanWithoutASecondScanClick() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val coordinator = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow { awaitCancellation() } }), backgroundScope)
        val vm = FolderViewModel(repo, coordinator, db, context, RepositoryFolderObservations(repo, coordinator), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            grant(vm, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            val id = requireNotNull(vm.state.value.resolvedFolderId)
            assertEquals("RUNNING", coordinator.observe(id).first().state)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun typedExactDevicePathReusesItsHeldRootGrant() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val granted = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
        val repo = FolderRepository(db, { setOf(granted) }, {}, { _, _ -> error("No picker grant should be acquired") })
        val id = repo.save(granted, "Books", ScanOptions())
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow { awaitCancellation() } }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onLocationEdited("/sdcard/Books/")
            vm.scan().join()
            assertEquals(id, vm.state.value.resolvedFolderId)
            assertEquals("RUNNING", scans.observe(id).first().state)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun computerPathScanExplainsDeviceGrantRatherThanSilentlyIgnoringInput() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onLocationEdited("/home/kent/Library")
            vm.scan().join()
            assertEquals(dev.kent.windbook.R.string.folder_device_path_hint, vm.state.value.message)
            assertNull(vm.state.value.resolvedFolderId)
            assertTrue(repo.observeFolders().first().isEmpty())
            assertNotNull(vm.state.value.pickerRequest)
            assertNull(vm.state.value.pickerRequest!!.initialUri)
            vm.onPickerCancelled(vm.state.value.pickerRequest!!.id)
            vm.setExtensions(emptySet()).join()
            vm.scan().join()
            assertNull(vm.state.value.pickerRequest)
            assertTrue(repo.observeFolders().first().isEmpty())
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun typedPathPrepositionsPickerAndStaleResultCannotTargetNewRoot() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        var acquisitions = 0
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> acquisitions++ })
        val b = repo.save("content://folders.test/tree/b", "B", ScanOptions())
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow {} }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onLocationEdited("/storage/emulated/0/Books")
            vm.scan().join()
            val request = requireNotNull(vm.state.value.pickerRequest)
            assertEquals("primary:Books", android.provider.DocumentsContract.getDocumentId(request.initialUri!!))
            vm.selectFolder(b).join()
            vm.onTreeGranted(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION, request.id).join()
            assertEquals(b, vm.state.value.resolvedFolderId)
            assertEquals(0, acquisitions)
            assertEquals(1, repo.observeFolders().first().size)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun newFoldersUseSettingsDefaultsWhileSavedFoldersKeepOverrides() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val prefs = preferences(backgroundScope)
        prefs.setScanOptions(ScanOptions(setOf(BookFormat.TXT), false))
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val saved = repo.save("content://folders.test/tree/saved", "Saved", ScanOptions(setOf(BookFormat.PDF), true))
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow { awaitCancellation() } }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, prefs)
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.state.first { it.options.extensions == setOf(BookFormat.TXT) }
            grant(vm, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            assertEquals(ScanOptions(setOf(BookFormat.TXT), false), repo.observeFolders().first().first { it.id == vm.state.value.resolvedFolderId }.options)
            vm.selectFolder(saved).join()
            assertEquals(ScanOptions(setOf(BookFormat.PDF), true), vm.state.value.options)
            prefs.setScanOptions(ScanOptions(setOf(BookFormat.EPUB), false))
            assertEquals(ScanOptions(setOf(BookFormat.PDF), true), vm.state.value.options)
            vm.onLocationEdited("/sdcard/New")
            vm.state.first { it.options == ScanOptions(setOf(BookFormat.EPUB), false) }
            vm.scan().join()
            grant(vm, Uri.parse("content://folders.test/tree/new"), Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
            assertEquals(ScanOptions(setOf(BookFormat.EPUB), false), vm.state.value.options)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun typedGrantUsesVisibleOptionsRatherThanHiddenSavedOptions() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val granted = "content://com.android.externalstorage.documents/tree/primary%3ABooks"
        val repo = FolderRepository(db, { setOf(granted) }, {}, { _, _ -> })
        val id = repo.save(granted, "Books", ScanOptions(setOf(BookFormat.TXT), false))
        val prefs = preferences(backgroundScope)
        prefs.setScanOptions(ScanOptions(setOf(BookFormat.PDF), true))
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow { awaitCancellation() } }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, prefs)
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.state.first { it.options.extensions == setOf(BookFormat.PDF) }
            vm.onLocationEdited("/sdcard/Books")
            vm.scan().join()
            assertEquals(ScanOptions(setOf(BookFormat.PDF), true), vm.state.value.options)
            assertEquals(ScanOptions(setOf(BookFormat.PDF), true), repo.observeFolders().first().single().options)
            assertEquals("RUNNING", scans.observe(id).first().state)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun pickerCompletionUsesTheOptionsShownWhenScanWasRequested() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val scans = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ -> flow { awaitCancellation() } }), backgroundScope)
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            vm.onLocationEdited("/sdcard/Books")
            vm.setOptions(ScanOptions(setOf(BookFormat.PDF), false)).join()
            vm.scan().join()
            val request = requireNotNull(vm.state.value.pickerRequest)
            vm.setOptions(ScanOptions(setOf(BookFormat.TXT), true)).join()
            vm.onTreeGranted(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION, request.id).join()
            assertEquals(ScanOptions(setOf(BookFormat.PDF), false), repo.observeFolders().first().single().options)
            assertEquals(ScanOptions(setOf(BookFormat.PDF), false), vm.state.value.options)
        } finally { store.clear(); vm.viewModelScope.coroutineContext[Job]?.join(); db.close() }
    }

    @Test fun scanOutlivesViewModelAndOptionChangesReplaceDurableRun() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        val repo = FolderRepository(db, { emptySet() }, {}, { _, _ -> })
        val coordinator = FolderScanCoordinator(repo, FolderScanner(TreeAccess { _, _ ->
            flow { awaitCancellation() }
        }), backgroundScope)
        val vm = FolderViewModel(repo, coordinator, db, context, RepositoryFolderObservations(repo, coordinator), unusedOpener, preferences(backgroundScope))
        val store = ViewModelStore().also { it.put("folder", vm) }
        try {
            grant(vm, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION).join()
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
        val vm = FolderViewModel(repo, scans, db, context, inputs, unusedOpener, preferences(backgroundScope))
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
        val vm = FolderViewModel(repo, scans, db, context, RepositoryFolderObservations(repo, scans), unusedOpener, preferences(backgroundScope))
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
            assertEquals(setOf(BookFormat.EPUB, BookFormat.PDF, BookFormat.TXT), vm.state.value.options.extensions)
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
