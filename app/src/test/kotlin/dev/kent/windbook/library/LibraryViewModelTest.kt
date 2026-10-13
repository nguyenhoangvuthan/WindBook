package dev.kent.windbook.library

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.kent.windbook.core.data.LibraryPreferences
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportError
import dev.kent.windbook.core.data.importer.ImportSource
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.LibrarySort
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.core.model.StatusFilter
import dev.kent.windbook.testing.MainDispatcherRule
import dev.kent.windbook.testing.TestBooks
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var repo: LibraryRepository
    private lateinit var importer: BookImporter
    private val files = mutableMapOf<String, File>()

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        val store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store)
        // No metadata title, so the importer falls back to the display name ("Dune.epub" -> "Dune").
        importer = BookImporter(db.libraryDao(), store, metadata = { _, _ ->
            Result.success(ExtractedMetadata(null, "Author", null))
        })
    }

    @After fun tearDown() = db.close()

    private fun viewModel(scopeForPrefs: kotlinx.coroutines.CoroutineScope): LibraryViewModel {
        val prefs = LibraryPreferences(PreferenceDataStoreFactory.create(scope = scopeForPrefs) { File(tmp.root, "lib.preferences_pb") })
        val sources = ImportSourceFactory { uri ->
            val file = files.getValue(uri.toString())
            object : ImportSource {
                override val displayName = file.name
                override val sourceUri = uri.toString()
                override fun open() = file.inputStream()
            }
        }
        return LibraryViewModel(repo, prefs, importer, sources)
    }

    private fun uriFor(file: File): Uri = Uri.parse("content://test/${file.name}").also { files[it.toString()] = file }

    @Test fun importThenSearchAndFilter() = runTest {
        val vm = viewModel(backgroundScope)
        val dune = uriFor(TestBooks.epub(tmp.root, "Dune.epub"))
        val emma = uriFor(TestBooks.epub(tmp.root, "Emma.epub", title = "Emma"))

        vm.messages.test {
            vm.import(listOf(dune, emma))
            assertEquals(LibraryMessage.Imported(2), awaitItem())
        }

        vm.state.test {
            var state = awaitItem()
            while (state.items.size < 2) state = awaitItem()
            assertFalse(state.isLibraryEmpty)
            assertEquals(listOf("Dune", "Emma"), state.items.map { it.book.title }.sorted())

            vm.onQueryChange("emm")
            while (state.items.size != 1) state = awaitItem()
            assertEquals("Emma", state.items.single().book.title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun duplicateImportReportsAlreadyInLibrary() = runTest {
        val vm = viewModel(backgroundScope)
        val uri = uriFor(TestBooks.epub(tmp.root, "Dune.epub"))
        vm.messages.test {
            vm.import(listOf(uri))
            awaitItem()
            vm.import(listOf(uri))
            assertEquals(LibraryMessage.AlreadyInLibrary::class, awaitItem()::class)
        }
    }

    @Test fun badFileReportsFailureWithName() = runTest {
        val vm = viewModel(backgroundScope)
        val uri = uriFor(tmp.newFile("notes.txt").apply { writeText("hi") })
        vm.messages.test {
            vm.import(listOf(uri))
            assertEquals(LibraryMessage.ImportFailed("notes.txt", ImportError.UNSUPPORTED_FORMAT), awaitItem())
        }
    }

    @Test fun statusChangeAndDelete() = runTest {
        val vm = viewModel(backgroundScope)
        vm.messages.test {
            vm.import(listOf(uriFor(TestBooks.epub(tmp.root, "Dune.epub"))))
            awaitItem()
        }
        val id = repo.observeLibrary().first().single().book.id

        vm.setStatus(id, ReadingStatus.FINISHED)
        vm.onFilterChange(StatusFilter.FINISHED)
        vm.onSortChange(LibrarySort.TITLE)
        vm.state.test {
            var state = awaitItem()
            while (state.items.isEmpty() || state.sort != LibrarySort.TITLE) state = awaitItem()
            assertEquals(ReadingStatus.FINISHED, state.items.single().book.status)
            cancelAndIgnoreRemainingEvents()
        }

        vm.delete(id)
        // delete() runs on viewModelScope and hops to Room's IO dispatcher; wait for the table to reflect it.
        repo.observeLibrary().first { it.isEmpty() }
        assertNull(repo.book(id))
    }
}
