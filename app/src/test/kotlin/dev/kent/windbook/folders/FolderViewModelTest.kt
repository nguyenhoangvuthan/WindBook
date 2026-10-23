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
        val vm = FolderViewModel(repo, coordinator, db, context)
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
        val vm = FolderViewModel(repo, coordinator, db, context)
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

}
