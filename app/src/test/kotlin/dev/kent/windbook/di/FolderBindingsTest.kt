package dev.kent.windbook.di

import android.content.Context
import android.content.Intent
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.FolderEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderBindingsTest {
    @Test fun disconnect_usesRealGrantReleaseBinding() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = WindBookDatabase.inMemory(context, AndroidSQLiteDriver())
        try {
            val tree = "content://bindings.test/tree/root"
            val grants = AppModule.folderGrants(context)
            grants.persistRead(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            db.folderDao().upsert(FolderEntity("folder", tree, "Synthetic", "[]", true))
            AppModule.bookSources(db, grants).disconnectFolder("folder")
            assertFalse(grants.hasRead(tree))
            assertNotNull(db.folderDao().find("folder")!!.disconnectedAt)
        } finally { db.close() }
    }
}
