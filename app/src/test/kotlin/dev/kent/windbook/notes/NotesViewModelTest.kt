package dev.kent.windbook.notes

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import dev.kent.windbook.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesViewModelTest {
    @get:Rule val main=MainDispatcherRule()
    private lateinit var db: WindBookDatabase
    private lateinit var vm: NotesViewModel
    private val anchor=PdfTextAnchor(page=0,width=400f,height=600f,rects=listOf(PdfTextRect(.1f,.2f,.5f,.3f)),quote="Quote")
    @Before fun setup()=runTest {
        db=WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(),AndroidSQLiteDriver())
        db.libraryDao().insert(BookEntity("b","Book",null,"PDF",null,1,"sha",null,null,"TO_READ",1,null,1,null))
        vm=NotesViewModel(db)
    }
    @After fun close()=db.close()
    @Test fun failedWriteKeepsDraftAndRetrySavesItExactlyOnce()=runTest {
        vm.begin("b","wrong",anchor)
        vm.changeBody("Draft body")
        vm.save(); vm.saving.first { !it }
        assertEquals("Draft body",vm.editor.value!!.body)
        assertTrue(vm.failed.value)
        assertFalse(vm.saving.value)
        vm.cancel()
        vm.begin("b","sha",anchor); vm.changeBody("Draft body")
        vm.save(); vm.save(); vm.saving.first { !it }
        assertNull(vm.editor.value)
        assertEquals(1,db.annotationDao().observe(null).first().size)
    }
    @Test fun cancelAndBlankNotesNeverWrite()=runTest {
        vm.begin("b","sha",anchor); vm.changeBody(" "); vm.save(); vm.saving.first { !it }
        assertNotNull(vm.editor.value)
        vm.cancel()
        assertNull(vm.editor.value)
        assertFalse(db.annotationDao().hasAnnotations("b"))
    }
}
