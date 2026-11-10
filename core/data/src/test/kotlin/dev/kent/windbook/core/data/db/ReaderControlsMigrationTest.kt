package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReaderControlsMigrationTest {
    @get:Rule val tmp=TemporaryFolder()
    @Test fun sevenToEightRetainsEveryExistingTableAndAddsEmptyNavigationTables()=runTest {
        val path=tmp.root.resolve("reading.db").path
        val schema=JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/7.json").readText()).getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { c ->
            val tables=schema.getJSONArray("entities")
            for(i in 0 until tables.length()) {
                val t=tables.getJSONObject(i); val name=t.getString("tableName")
                c.execSQL(t.getString("createSql").replace("\${TABLE_NAME}",name))
                val indexes=t.optJSONArray("indices") ?: continue
                for(j in 0 until indexes.length()) c.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) c.execSQL(setup.getString(i))
            c.execSQL("INSERT INTO books VALUES ('b','Synthetic',NULL,'PDF',NULL,10,'sha',NULL,NULL,'READING',1,2,3,NULL,NULL)")
            c.execSQL("INSERT INTO reading_progress VALUES ('b','kept',0.4,9)")
            c.execSQL("INSERT INTO book_preferences VALUES ('b','{\"unknown\":1}',9)")
            c.execSQL("INSERT INTO text_reading_states VALUES ('b','UTF8','text',0.5,9)")
            c.execSQL("INSERT INTO annotations VALUES ('a','b','sha','{}','Quote','GOLD','Body',1,2)")
            c.execSQL("PRAGMA user_version=7")
        }
        val db=WindBookDatabase.open(ApplicationProvider.getApplicationContext(),path,AndroidSQLiteDriver())
        try {
            assertEquals("kept",db.libraryDao().progress("b")!!.locatorJson)
            assertEquals("{\"unknown\":1}",db.bookPreferencesDao().json("b"))
            assertEquals("text",db.sourceDao().textProgress("b","UTF8")!!.locatorJson)
            assertEquals("Body",db.annotationDao().find("a")!!.body)
            assertFalse(db.bookmarkDao().hasBookmarks("b"))
            assertNull(db.pdfContentsDao().find("b","sha",1))
        } finally { db.close() }
        AndroidSQLiteDriver().open(path).use { c ->
            c.prepare("PRAGMA user_version").use { assertTrue(it.step());assertEquals(8L,it.getLong(0)) }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
}
