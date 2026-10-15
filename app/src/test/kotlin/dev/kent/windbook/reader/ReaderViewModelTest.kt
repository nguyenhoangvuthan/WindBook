package dev.kent.windbook.reader

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var library: LibraryRepository
    private lateinit var prefs: DisplayPreferencesRepository
    private lateinit var bookFile: File

    private class FakeSession(override val format: BookFormat) : ReaderSession {
        var closed = false
        override fun close() { closed = true }
    }

    private val opener = BookOpener { _, format -> Result.success(FakeSession(format)) }

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        library = LibraryRepository(db.libraryDao(), BookFileStore(tmp.newFolder("app")))
        prefs = DisplayPreferencesRepository(db.bookPreferencesDao())
        bookFile = tmp.newFile("a.epub")
        db.libraryDao().insert(
            BookEntity("a", "Dune", null, "EPUB", bookFile.absolutePath, 1, "sha", null, null, "TO_READ", 1, null, 1, null),
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun ReaderViewModel.awaitSettled(): ReaderUiState =
        state.first { it !is ReaderUiState.Loading }

    @Test fun ready_containsSavedLocator() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)

        val state = ReaderViewModel("a", library, opener, prefs).awaitSettled() as ReaderUiState.Ready

        assertEquals("Dune", state.title)
        assertEquals("""{"href":"c3"}""", state.initialLocatorJson)
    }

    @Test fun opening_marksBookReading() = runTest {
        ReaderViewModel("a", library, opener, prefs).awaitSettled()
        assertEquals(ReadingStatus.READING, library.book("a")!!.status)
    }

    @Test fun unknownBook_isNotFound() = runTest {
        assertEquals(ReaderUiState.Error(ReaderError.NOT_FOUND), ReaderViewModel("zzz", library, opener, prefs).awaitSettled())
    }

    @Test fun missingFile_isFileMissing() = runTest {
        bookFile.delete()
        assertEquals(ReaderUiState.Error(ReaderError.FILE_MISSING), ReaderViewModel("a", library, opener, prefs).awaitSettled())
    }

    @Test fun openerFailure_isCannotOpenAndBookStaysToRead() = runTest {
        val failing = BookOpener { _, _ -> Result.failure(IllegalStateException("broken")) }
        assertEquals(ReaderUiState.Error(ReaderError.CANNOT_OPEN), ReaderViewModel("a", library, failing, prefs).awaitSettled())
        assertEquals(ReadingStatus.TO_READ, library.book("a")!!.status)
    }

    @Test fun latestLocator_startsFromSavedAndFollowsChanges() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)
        val vm = ReaderViewModel("a", library, opener, prefs)
        vm.awaitSettled()
        assertEquals("""{"href":"c3"}""", vm.latestLocatorJson)

        vm.onLocatorChanged("""{"href":"c4"}""", 0.7, "Chapter Four")

        assertEquals("""{"href":"c4"}""", vm.latestLocatorJson)
        assertEquals("Chapter Four", vm.chapterTitle.value)
    }

    @Test fun nearTheEnd_offersMarkFinished() = runTest {
        val vm = ReaderViewModel("a", library, opener, prefs)
        vm.awaitSettled()
        assertEquals(false, vm.canMarkFinished.value)

        vm.onLocatorChanged("""{"href":"end"}""", 0.995, null)
        assertEquals(true, vm.canMarkFinished.value)

        vm.markFinished()
        assertEquals(false, vm.canMarkFinished.value)
        var status = library.book("a")!!.status
        val deadline = System.currentTimeMillis() + 5_000
        while (status != ReadingStatus.FINISHED && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            status = library.book("a")!!.status
        }
        assertEquals(ReadingStatus.FINISHED, status)
    }

    @Test fun removeBook_deletesThenCallsBack() = runTest {
        bookFile.delete()
        val vm = ReaderViewModel("a", library, opener, prefs)
        vm.awaitSettled()
        val removed = kotlinx.coroutines.CompletableDeferred<Unit>()

        vm.removeBook { removed.complete(Unit) }
        removed.await()

        assertEquals(null, library.book("a"))
    }

    @Test fun rapidLocatorChanges_persistLastLocator() = runTest {
        val vm = ReaderViewModel("a", library, opener, prefs)
        vm.awaitSettled()

        repeat(100) { i -> vm.onLocatorChanged("""{"href":"c","i":$i}""", i / 100.0, null) }
        advanceUntilIdle()
        // Room writes run on Dispatchers.IO; poll until the conflated writer catches up.
        var saved = library.savedLocator("a")
        val deadline = System.currentTimeMillis() + 5_000
        while (saved?.contains("\"i\":99") != true && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            saved = library.savedLocator("a")
        }

        assertTrue(saved!!.contains("\"i\":99"))
        assertEquals(0.99, vm.progression.value, 1e-9)
    }

    @Test fun toggleOverlay_flips() = runTest {
        val vm = ReaderViewModel("a", library, opener, prefs)
        val before = vm.overlayVisible.value
        vm.toggleOverlay()
        assertEquals(!before, vm.overlayVisible.value)
    }
}
