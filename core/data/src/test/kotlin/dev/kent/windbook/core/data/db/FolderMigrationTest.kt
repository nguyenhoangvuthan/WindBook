package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.BookSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderMigrationTest {
    @get:Rule val tmp = TemporaryFolder()
    private val locator = """{"href":"publication.pdf","locations":{"fragments":["page=7"],"position":7}}"""
    private val prefs = """{"pdfLayout":"PAGED","future":{"setting":42}}"""

    private fun createV2(path: String) {
        val schema = JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/2.json").readText())
            .getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    val index = indices.getJSONObject(j)
                    connection.execSQL(index.getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) connection.execSQL(setup.getString(i))
            connection.execSQL("INSERT INTO books VALUES ('kept','A',NULL,'EPUB','/owned/a.epub',10,'sha-a',NULL,NULL,'READING',1,2,3,NULL)")
            connection.execSQL("INSERT INTO books VALUES ('hidden','B',NULL,'PDF','/owned/b.pdf',20,'sha-b',NULL,NULL,'FINISHED',4,5,6,7)")
            for (id in listOf("kept", "hidden")) {
                connection.execSQL("INSERT INTO reading_progress VALUES ('$id','$locator',0.5,8)")
                connection.execSQL("INSERT INTO book_preferences VALUES ('$id','$prefs',9)")
            }
            connection.execSQL("PRAGMA user_version = 2")
        }
    }

    @Test fun v3Migration_preservesSourcesMembershipsRunsAndReadingData() = runTest {
        val path = File(tmp.root, "v3.db").absolutePath
        val schema = JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/3.json").readText())
            .getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.execSQL("PRAGMA foreign_keys = ON")
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) connection.execSQL(
                    indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) connection.execSQL(setup.getString(i))
            connection.execSQL("INSERT INTO books VALUES ('external','Synthetic.pdf',NULL,'PDF',NULL,10,NULL,NULL,NULL,'READING',1,2,3,4)")
            connection.execSQL("INSERT INTO reading_progress VALUES ('external','$locator',0.5,8)")
            connection.execSQL("INSERT INTO book_preferences VALUES ('external','$prefs',9)")
            connection.execSQL("INSERT INTO folders VALUES ('folder','content://migration.test/tree/root','Root','[\"PDF\"]',1,NULL,9)")
            connection.execSQL("INSERT INTO book_sources VALUES ('source','external','EXTERNAL',NULL,'content://migration.test/tree/root/document/42','migration.test','42',10,2,'verified','AVAILABLE')")
            connection.execSQL("INSERT INTO folder_memberships VALUES ('folder','source','Synthetic.pdf','root','old')")
            connection.execSQL("INSERT INTO scan_runs VALUES ('old','folder','{}','INCOMPLETE',5,3,2,1,0,1)")
            connection.execSQL("PRAGMA user_version = 3")
        }
        val db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        try {
            assertEquals(locator, db.libraryDao().progress("external")!!.locatorJson)
            assertEquals(prefs, db.bookPreferencesDao().json("external"))
            assertEquals(4L, db.libraryDao().findIncludingHidden("external")!!.deletedAt)
            assertEquals("verified", db.sourceDao().forBook("external").single().verifiedSha256)
            val membership = db.folderDao().memberships("source").single()
            assertTrue(membership.present)
            assertEquals("old", membership.lastSeenRun)
            val run = db.folderDao().run("old")!!
            assertEquals(5L, run.examined); assertEquals(1L, run.directoryErrors)
            assertEquals(0L, run.startedAt); assertEquals("", run.currentPath)
        } finally { db.close() }
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA user_version").use { assertTrue(it.step()); assertEquals(5L, it.getLong(0)) }
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }

    @Test fun v2Migration_preservesHiddenBooksProgressPrefsAndOwnedSources() = runTest {
        val path = File(tmp.root, "migration.db").absolutePath
        createV2(path)
        val db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        try {
            val sources = BookSourcesRepository(db, FolderRepository(db, { emptySet() }, {}, { _, _ -> error("No picker acquisition expected") }))
            assertEquals(setOf("kept", "hidden"), db.libraryDao().allIdsIncludingHidden().toSet())
            assertNull(db.libraryDao().findById("hidden"))
            assertEquals(7L, db.libraryDao().findIncludingHidden("hidden")!!.deletedAt)
            for (id in listOf("kept", "hidden")) {
                assertEquals(locator, db.libraryDao().progress(id)!!.locatorJson)
                assertEquals(prefs, db.bookPreferencesDao().json(id))
            }
            assertEquals(listOf(BookSource.Owned("/owned/a.epub")), sources.sources("kept"))
            assertEquals(listOf(BookSource.Owned("/owned/b.pdf")), sources.sources("hidden"))
            assertEquals(2, db.sourceDao().countOwned())
            assertEquals("sha-b", db.sourceDao().forBook("hidden").single().verifiedSha256)
        } finally { db.close() }
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
            val indices = mutableMapOf<String, Long>()
            connection.prepare("PRAGMA index_list('books')").use { stmt ->
                while (stmt.step()) indices[stmt.getText(1)] = stmt.getLong(2)
            }
            assertEquals(0L, indices["index_books_sha256"])
            assertEquals(0L, indices["index_books_last_opened_at"])
            connection.execSQL("PRAGMA foreign_keys = ON")
            connection.execSQL("DELETE FROM books WHERE id = 'kept'")
            for (table in listOf("reading_progress", "book_preferences", "book_sources")) {
                connection.prepare("SELECT COUNT(*) FROM $table WHERE book_id = 'kept'").use {
                    assertTrue(it.step()); assertEquals(0L, it.getLong(0))
                }
            }
        }
    }
}
