package dev.kent.windbook.core.data.db

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.AnnotationRepository
import dev.kent.windbook.core.model.*
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
class AnnotationMigrationTest {
    @get:Rule val tmp=TemporaryFolder()
    @Test fun migrationFromExportedSixPreservesAllPriorDataAndSavedNoteSurvivesReopen()=runTest {
        val path=tmp.root.resolve("notes.db").path
        val schema=JSONObject(File("schemas/dev.kent.windbook.core.data.db.WindBookDatabase/6.json").readText()).getJSONObject("database")
        AndroidSQLiteDriver().open(path).use { c ->
            val tables=schema.getJSONArray("entities")
            for(i in 0 until tables.length()) {
                val table=tables.getJSONObject(i); val name=table.getString("tableName")
                c.execSQL(table.getString("createSql").replace("\${TABLE_NAME}",name))
                val indexes=table.optJSONArray("indices") ?: continue
                for(j in 0 until indexes.length()) c.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",name))
            }
            val setup=schema.getJSONArray("setupQueries")
            for(i in 0 until setup.length()) c.execSQL(setup.getString(i))
            c.execSQL("INSERT INTO books VALUES ('b','Synthetic',NULL,'PDF',NULL,10,'sha',NULL,NULL,'READING',1,2,3,NULL,NULL)")
            c.execSQL("INSERT INTO reading_progress VALUES ('b','kept-locator',0.4,9)")
            c.execSQL("INSERT INTO book_preferences VALUES ('b','{\"future\":1}',9)")
            c.execSQL("INSERT INTO text_reading_states VALUES ('b','UTF8','text-locator',0.5,9)")
            c.execSQL("PRAGMA user_version=6")
        }
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val anchor=PdfTextAnchor(page=0,width=400f,height=600f,rects=listOf(PdfTextRect(.1f,.1f,.2f,.2f)),quote="Synthetic")
        val db=WindBookDatabase.open(context,path,AndroidSQLiteDriver())
        val id=try {
            assertEquals("kept-locator",db.libraryDao().progress("b")!!.locatorJson)
            assertEquals("{\"future\":1}",db.bookPreferencesDao().json("b"))
            assertEquals("text-locator",db.sourceDao().textProgress("b","UTF8")!!.locatorJson)
            AnnotationRepository(db).save("b","sha",anchor,AnnotationColor.GOLD,"Saved")
        } finally { db.close() }
        val reopened=WindBookDatabase.open(context,path,AndroidSQLiteDriver())
        try { assertEquals("Saved",AnnotationRepository(reopened).find(id)!!.body) } finally { reopened.close() }
        AndroidSQLiteDriver().open(path).use { c ->
            c.prepare("PRAGMA user_version").use { assertTrue(it.step()); assertEquals(7L,it.getLong(0)) }
            c.prepare("PRAGMA foreign_key_check").use { assertFalse(it.step()) }
        }
    }
}
