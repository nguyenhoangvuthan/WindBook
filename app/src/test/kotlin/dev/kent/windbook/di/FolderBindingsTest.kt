package dev.kent.windbook.di

import android.content.Context
import android.content.Intent
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.FolderEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.ScanOptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderBindingsTest {
    @Test fun connect_usesRealReadOnlyGrantAcquisitionBinding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        try {
            val tree = "content://bindings.test/tree/reconnected"
            val grants = AppModule.folderGrants(context)
            db.folderDao().upsert(FolderEntity("folder", tree, "Old", "[]", true, disconnectedAt = 1))
            val folders = AppModule.folders(db, grants)
            assertEquals("folder", folders.connect(tree, "Reconnected", ScanOptions(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            assertTrue(grants.hasRead(tree))
            assertFalse(context.contentResolver.persistedUriPermissions.single { it.uri.toString() == tree }.isWritePermission)
            assertNull(db.folderDao().find("folder")!!.disconnectedAt)
            folders.disconnectFolder("folder")
            assertFalse(grants.hasRead(tree))
        } finally { db.close() }
    }

    @Test fun disconnect_usesRealGrantReleaseBinding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        try {
            val tree = "content://bindings.test/tree/root"
            val grants = AppModule.folderGrants(context)
            grants.persistRead(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            db.folderDao().upsert(FolderEntity("folder", tree, "Synthetic", "[]", true))
            AppModule.bookSources(db, AppModule.folders(db, grants)).disconnectFolder("folder")
            assertFalse(grants.hasRead(tree))
            assertNotNull(db.folderDao().find("folder")!!.disconnectedAt)
        } finally { db.close() }
    }
}
