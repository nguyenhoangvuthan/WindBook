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
class EpubAnnotationMigrationTest {
    @get:Rule val tmp=TemporaryFolder()
    @Test fun eightToNinePreservesPdfPayloadAndOtherDurableRows()=runTest {
        val path=tmp.root.resolve("old.db").path
        val schema=JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/8.json").readText()).getJSONObject("database")
        AndroidSQLiteDriver().open(path).use {c ->
            val entities=schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val e=entities.getJSONObject(i);val name=e.getString("tableName")
                c.execSQL(e.getString("createSql").replace("\${TABLE_NAME}",name))
                val indices=e.optJSONArray("indices") ?: continue
                for(j in 0 until indices.length()) c.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) c.execSQL(setup.getString(i))
            c.execSQL("INSERT INTO books VALUES ('b','Synthetic',NULL,'PDF',NULL,10,'sha',NULL,NULL,'READING',1,2,3,NULL,NULL)")
            c.execSQL("INSERT INTO annotations VALUES ('a','b','sha','{future: raw bytes}','quote','BLUE','body',4,5)")
            c.execSQL("INSERT INTO reading_progress VALUES ('b','locator',0.4,9)")
            c.execSQL("INSERT INTO book_preferences VALUES ('b','{\"future\":1}',9)")
            c.execSQL("INSERT INTO bookmarks VALUES ('k','b','sha','locator',0.4,'label',NULL,4,5,NULL)")
            c.execSQL("PRAGMA user_version=8")
        }
        val db=WindBookDatabase.open(ApplicationProvider.getApplicationContext(),path,AndroidSQLiteDriver())
        try {
            assertEquals(AnnotationEntity("a","b","sha","{future: raw bytes}","quote","BLUE","body",4,5,"PDF_TEXT"),db.annotationDao().find("a"))
            assertEquals("locator",db.libraryDao().progress("b")!!.locatorJson)
            assertEquals("{\"future\":1}",db.bookPreferencesDao().json("b"))
        } finally {db.close()}
        AndroidSQLiteDriver().open(path).use {c ->
            c.prepare("SELECT locator_json,label FROM bookmarks WHERE id='k'").use {assertTrue(it.step());assertEquals("locator",it.getText(0));assertEquals("label",it.getText(1))}
            c.prepare("PRAGMA foreign_key_check").use {assertFalse(it.step())}
        }
    }
}
