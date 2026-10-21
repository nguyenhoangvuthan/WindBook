package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Builds a v1 database exactly as M1 shipped it, from the exported schema. */
    private fun createV1(path: String) {
        val schema = JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/1.json").readText())
            .getJSONObject("database")
        val connection = AndroidSQLiteDriver().open(path)
        val entities = schema.getJSONArray("entities")
        for (i in 0 until entities.length()) {
            val entity = entities.getJSONObject(i)
            val table = entity.getString("tableName")
            connection.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (j in 0 until indices.length()) {
                val index = indices.getJSONObject(j)
                connection.execSQL(
                    index.getString("createSql").replace("\${TABLE_NAME}", table).replace("\${INDEX_NAME}", index.getString("name")),
                )
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (i in 0 until setup.length()) connection.execSQL(setup.getString(i))
        connection.execSQL(
            "INSERT INTO books VALUES ('a','Dune',NULL,'PDF','/f',1,'sha',NULL,NULL,'READING',1,2,3,NULL)",
        )
        connection.execSQL("INSERT INTO reading_progress VALUES ('a','{\"href\":\"x\"}',0.5,3)")
        connection.execSQL("PRAGMA user_version = 1")
        connection.close()
    }

    @Test fun v1DatabaseMigratesKeepingBooksAndProgress() = runTest {
        val path = File(tmp.root, "windbook.db").absolutePath
        createV1(path)

        val db = WindBookDatabase.open(ApplicationProvider.getApplicationContext(), path, AndroidSQLiteDriver())
        assertNotNull(db.libraryDao().findById("a"))
        assertEquals("/f", db.sourceDao().forBook("a").single().localPath)
        assertEquals("""{"href":"x"}""", db.libraryDao().progress("a")!!.locatorJson)
        db.bookPreferencesDao().upsert(BookPreferencesEntity("a", "{}", 4))
        assertEquals("{}", db.bookPreferencesDao().json("a"))
        db.close()
    }
}
