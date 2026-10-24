package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
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
class RevisionMigrationTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun v5MigrationPreservesExistingSourceAndReadingIdentity() = runTest {
        val path = File(tmp.root, "v5.db").path
        val schema = JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/5.json").readText()).getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { connection ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
            connection.execSQL("INSERT INTO books VALUES ('old','Synthetic.txt',NULL,'TXT',NULL,10,'same',NULL,NULL,'READING',1,2,3,NULL)")
            connection.execSQL("INSERT INTO reading_progress VALUES ('old','old-anchor',0.5,8)")
            connection.execSQL("INSERT INTO book_sources VALUES ('source','old','EXTERNAL',NULL,'content://test/document/a','test','a',10,2,'same','AVAILABLE')")
            connection.execSQL("PRAGMA user_version = 5")
        }
        val db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        try {
            assertEquals("same", db.libraryDao().findIncludingHidden("old")!!.sha256)
            assertEquals("old-anchor", db.libraryDao().progress("old")!!.locatorJson)
            val row = db.sourceDao().forBook("old").single()
            assertEquals("content://test/document/a", row.uri)
            assertNull(row.txtEncodingOverride)
            db.sourceDao().upsertTextProgress(TextReadingStateEntity("old", "UTF8", "new-anchor", .7, 9))
            assertEquals("new-anchor", db.sourceDao().textProgress("old", "UTF8")!!.locatorJson)
        } finally { db.close() }
    }
    @Test fun v4MigrationAllowsReadConflictsAndRetainsHiddenProgressPreferencesAndSources() = runTest {
        val path = File(tmp.root, "v4.db").path
        val schema = JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/4.json").readText()).getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { connection ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) connection.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val queries = schema.getJSONArray("setupQueries")
            for (i in 0 until queries.length()) connection.execSQL(queries.getString(i))
            connection.execSQL("INSERT INTO books VALUES ('old','Synthetic.pdf',NULL,'PDF',NULL,10,'same',NULL,NULL,'READING',1,2,3,4)")
            connection.execSQL("INSERT INTO reading_progress VALUES ('old','old-anchor',0.5,8)")
            connection.execSQL("INSERT INTO book_preferences VALUES ('old','{\"unknown\":42}',9)")
            connection.execSQL("INSERT INTO book_sources VALUES ('source','old','EXTERNAL',NULL,'content://test/document/a','test','a',10,2,'same','AVAILABLE')")
            connection.execSQL("PRAGMA user_version = 4")
        }
        val db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        try {
            val old = db.libraryDao().findIncludingHidden("old")!!
            db.libraryDao().insert(old.copy(id = "conflict", deletedAt = null))
            assertEquals("old-anchor", db.libraryDao().progress("old")!!.locatorJson)
            assertEquals("{\"unknown\":42}", db.bookPreferencesDao().json("old"))
            assertEquals(4L, db.libraryDao().findIncludingHidden("old")!!.deletedAt)
            assertEquals("same", db.sourceDao().forBook("old").single().verifiedSha256)
            assertEquals("conflict", db.libraryDao().findBySha("same")!!.id)
        } finally { db.close() }
        AndroidSQLiteDriver().open(path).use { connection ->
            connection.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
}
