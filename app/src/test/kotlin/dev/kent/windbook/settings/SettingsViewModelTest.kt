package dev.kent.windbook.settings

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.importer.BookImporter
import dev.kent.windbook.core.data.importer.ImportSource
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.testing.MainDispatcherRule
import dev.kent.windbook.testing.TestBooks
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var store: BookFileStore
    private lateinit var repo: LibraryRepository
    private lateinit var importer: BookImporter

    private val locales = object : AppLocaleController {
        var language = AppLanguage.SYSTEM
        val applied = mutableListOf<AppLanguage>()
        override fun current() = language
        override fun set(language: AppLanguage) {
            this.language = language
            applied += language
        }
    }

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        store = BookFileStore(tmp.newFolder("app"))
        repo = LibraryRepository(db.libraryDao(), store)
        importer = BookImporter(db.libraryDao(), store, metadata = { _, _ ->
            Result.success(ExtractedMetadata(null, "Author", null))
        })
    }

    @After fun tearDown() = db.close()

    private fun preferences(scope: kotlinx.coroutines.CoroutineScope): dev.kent.windbook.core.data.LibraryPreferences =
        dev.kent.windbook.core.data.LibraryPreferences(androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.root, "scan-${java.util.UUID.randomUUID()}.preferences_pb")
        })

    private fun source(file: File) = object : ImportSource {
        override val displayName = file.name
        override val sourceUri = file.toURI().toString()
        override fun open() = file.inputStream()
    }

    @Test fun scanChoicesPersistAndRapidChangesPreserveEachOther() = runTest {
        val prefs = preferences(backgroundScope)
        val vm = SettingsViewModel(repo, store, locales, AppInfo("1"), prefs)
        val recursive = vm.setScanRecursive(false)
        val text = vm.toggleScanExtension(dev.kent.windbook.core.model.BookFormat.TXT)
        recursive.join(); text.join()
        val expected = dev.kent.windbook.core.model.ScanOptions(setOf(dev.kent.windbook.core.model.BookFormat.EPUB,
            dev.kent.windbook.core.model.BookFormat.PDF, dev.kent.windbook.core.model.BookFormat.TXT), false)
        assertEquals(expected, prefs.scanOptions.first())
        val reopened = SettingsViewModel(repo, store, locales, AppInfo("1"), prefs)
        reopened.state.test {
            var state = awaitItem()
            while (state.scanOptions != expected) state = awaitItem()
            assertEquals(expected, state.scanOptions)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun storageCountsBooksAndTheirSizes() = runTest {
        val dune = TestBooks.epub(tmp.root, "Dune.epub")
        val emma = TestBooks.epub(tmp.root, "Emma.epub", title = "Emma")
        importer.import(source(dune))
        importer.import(source(emma))
        val vm = SettingsViewModel(repo, store, locales, AppInfo("9.9.9"), preferences(backgroundScope))

        vm.state.test {
            var state = awaitItem()
            while (state.bookCount < 2) state = awaitItem()
            assertEquals(dune.length() + emma.length(), state.totalBytes)
            assertTrue(state.freeBytes > 0)
            assertEquals("9.9.9", state.versionName)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun choosingALanguageAppliesItAndShowsIt() = runTest {
        val vm = SettingsViewModel(repo, store, locales, AppInfo("1"), preferences(backgroundScope))
        vm.setLanguage(AppLanguage.VIETNAMESE)
        assertEquals(listOf(AppLanguage.VIETNAMESE), locales.applied)
        vm.state.test {
            var state = awaitItem()
            while (state.language != AppLanguage.VIETNAMESE) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun firstFrameAlreadyShowsTheSavedLanguage() = runTest {
        locales.language = AppLanguage.VIETNAMESE
        val vm = SettingsViewModel(repo, store, locales, AppInfo("1"), preferences(backgroundScope))
        // Before the library emits, the screen must not flash "Automatic".
        assertEquals(AppLanguage.VIETNAMESE, vm.state.value.language)
    }

    @Test fun languageChangedInSystemSettingsShowsAfterRefresh() = runTest {
        val vm = SettingsViewModel(repo, store, locales, AppInfo("1"), preferences(backgroundScope))
        locales.language = AppLanguage.ENGLISH // e.g. Android 13+ "App languages" while the tab was open
        vm.refreshLanguage()
        vm.state.test {
            var state = awaitItem()
            while (state.language != AppLanguage.ENGLISH) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
