package dev.kent.windbook.reader

import androidx.lifecycle.ViewModelStore
import androidx.room3.withWriteTransaction
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import androidx.lifecycle.viewModelScope
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.data.db.BookSourceEntity
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.reader.source.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.ReadingStatus
import dev.kent.windbook.testing.MainDispatcherRule
import java.io.File
import kotlinx.coroutines.Job
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
    private lateinit var sources: BookSourcesRepository
    private lateinit var access: SourceAccess
    private lateinit var files: BookFileStore
    private var stamp = SourceStamp(null, null)
    private var duringAcquire: (() -> Unit)? = null
    private var deniedUris = emptySet<String>()
    private var metadata = SourceMetadata { _, _ -> Result.failure(Exception("No metadata")) }
    private val viewModels = mutableListOf<ReaderViewModel>()
    private val viewModelStore = ViewModelStore()

    private class FakeSession(override val format: BookFormat) : ReaderSession {
        var closed = false
        override fun close() { closed = true }
    }

    private val opener = BookOpener { _, format -> Result.success(FakeSession(format)) }

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        files = BookFileStore(tmp.newFolder("app"))
        library = LibraryRepository(db.libraryDao(), files)
        sources = BookSourcesRepository(db, FolderRepository(db, { emptySet() }, {}, { _, _ -> }))
        access = object : SourceAccess(ApplicationProvider.getApplicationContext()) {
            override suspend fun acquire(source: BookSource): SourceLease {
                duringAcquire?.invoke()
                return super.acquire(BookSource.Owned(bookFile.path))
            }
            override suspend fun metadata(source: BookSource): SourceStamp {
                if (source is BookSource.External && source.uri in deniedUris) throw SecurityException("Denied")
                return stamp
            }
        }
        prefs = DisplayPreferencesRepository(db.bookPreferencesDao())
        bookFile = tmp.newFile("a.epub")
        db.libraryDao().insert(
            BookEntity("a", "Dune", null, "EPUB", bookFile.absolutePath, 1, "sha", null, null, "TO_READ", 1, null, 1, null),
        )
    }

    @After fun tearDown() = runTest {
        val jobs = viewModels.mapNotNull { it.viewModelScope.coroutineContext[Job] }
        viewModelStore.clear()
        jobs.forEach { it.join() }
        if (::db.isInitialized) db.close()
    }

    private fun viewModel(id: String, bookOpener: BookOpener = opener): ReaderViewModel =
        ReaderViewModel(id, library, bookOpener, prefs, sources, access, metadata, files).also {
            viewModels.add(it)
            viewModelStore.put(viewModels.size.toString(), it)
        }

    private suspend fun ReaderViewModel.awaitSettled(): ReaderUiState =
        state.first { it !is ReaderUiState.Loading }

    @Test fun readableTextCanSwitchEncodingAndRestorePriorPassage() = runTest {
        db.libraryDao().update(db.libraryDao().findIncludingHidden("a")!!.copy(format = "TXT"))
        val textOpener = object : BookOpener {
            override suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession> = error("must use snapshot")
            override suspend fun openText(source: SourceLease, encoding: dev.kent.windbook.reader.formats.TextEncoding?): Result<ReaderSession> {
                source.close()
                return Result.success(object : ReaderSession {
                    override val format = BookFormat.TXT
                    override val textEncoding = encoding ?: dev.kent.windbook.reader.formats.TextEncoding.UTF8
                    override fun close() = Unit
                })
            }
        }
        val vm = viewModel("a", textOpener)
        val oldSession = (vm.awaitSettled() as ReaderUiState.Ready).session
        vm.onLocatorChanged("utf8-passage", .3, null)
        vm.chooseTextEncoding(dev.kent.windbook.reader.formats.TextEncoding.WINDOWS_1258)
        assertEquals(null, (vm.awaitSettled() as ReaderUiState.Ready).initialLocatorJson)
        vm.onLocatorChanged("old-late-callback", .9, null, expectedSession = oldSession)
        assertEquals(null, vm.latestLocatorJson)
        assertEquals(null, library.savedLocator("a"))
        vm.onLocatorChanged("windows-passage", .6, null)
        vm.chooseTextEncoding(dev.kent.windbook.reader.formats.TextEncoding.UTF8)
        assertEquals("utf8-passage", (vm.awaitSettled() as ReaderUiState.Ready).initialLocatorJson)
        assertEquals("utf8-passage", library.savedLocator("a"))
        assertEquals("UTF8", sources.textEncoding("a", BookSource.Owned(bookFile.path)))
    }
    @Test fun malformedTextOffersEncodingInsteadOfReconnect() = runTest {
        db.libraryDao().update(db.libraryDao().findIncludingHidden("a")!!.copy(format = "TXT"))
        val failing = object : BookOpener {
            override suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession> = error("must use acquired source")
            override suspend fun openText(source: SourceLease, encoding: dev.kent.windbook.reader.formats.TextEncoding?): Result<ReaderSession> {
                source.close()
                return Result.failure(dev.kent.windbook.reader.formats.TextEncodingException(java.nio.charset.MalformedInputException(1)))
            }
        }
        assertEquals(ReaderUiState.EncodingRequired, viewModel("a", failing).awaitSettled())
    }
    @Test fun ready_containsSavedLocator() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)

        val state = viewModel("a").awaitSettled() as ReaderUiState.Ready

        assertEquals("Dune", state.title)
        assertEquals("""{"href":"c3"}""", state.initialLocatorJson)
    }

    @Test fun opening_marksBookReading() = runTest {
        viewModel("a").awaitSettled()
        assertEquals(ReadingStatus.READING, library.book("a")!!.status)
    }

    @Test fun unknownBook_isNotFound() = runTest {
        assertEquals(ReaderUiState.Error(ReaderError.NOT_FOUND), viewModel("zzz").awaitSettled())
    }

    @Test fun missingFile_isFileMissing() = runTest {
        bookFile.delete()
        assertEquals(ReaderUiState.Error(ReaderError.FILE_MISSING), viewModel("a").awaitSettled())
    }

    @Test fun openerFailure_isCannotOpenAndBookStaysToRead() = runTest {
        val failing = BookOpener { _, _ -> Result.failure(IllegalStateException("broken")) }
        assertEquals(ReaderUiState.Error(ReaderError.CANNOT_OPEN), viewModel("a", failing).awaitSettled())
        assertEquals(ReadingStatus.TO_READ, library.book("a")!!.status)
    }

    @Test fun latestLocator_startsFromSavedAndFollowsChanges() = runTest {
        library.saveProgress("a", """{"href":"c3"}""", 0.6)
        val vm = viewModel("a")
        vm.awaitSettled()
        assertEquals("""{"href":"c3"}""", vm.latestLocatorJson)

        vm.onLocatorChanged("""{"href":"c4"}""", 0.7, "Chapter Four")

        assertEquals("""{"href":"c4"}""", vm.latestLocatorJson)
        assertEquals("Chapter Four", vm.chapterTitle.value)
    }

    @Test fun nearTheEnd_offersMarkFinished() = runTest {
        val vm = viewModel("a")
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
        val vm = viewModel("a")
        vm.awaitSettled()
        val removed = kotlinx.coroutines.CompletableDeferred<Unit>()

        vm.removeBook { removed.complete(Unit) }
        removed.await()

        assertEquals(null, library.book("a"))
    }

    @Test fun rapidLocatorChanges_persistLastLocator() = runTest {
        val vm = viewModel("a")
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

    @Test fun externalCandidateWithoutOwnedPath_isCannotOpenBeforeCallingOpener() = runTest {
        val old = db.libraryDao().findById("a")!!
        db.libraryDao().update(old.copy(filePath = null, sha256 = null))
        var calls = 0
        val observing = BookOpener { _, format -> calls++; Result.success(FakeSession(format)) }
        assertEquals(ReaderUiState.Reconnect,
            viewModel("a", observing).awaitSettled())
        assertEquals(0, calls)
        assertEquals(ReadingStatus.TO_READ, library.book("a")!!.status)
    }

    private suspend fun external(sha: String? = null) {
        db.libraryDao().update(db.libraryDao().findById("a")!!.copy(filePath = null, sha256 = sha))
        db.sourceDao().insert(BookSourceEntity("external", "a", "EXTERNAL", uri = "content://test/document/a",
            authority = "test", documentId = "a", verifiedSha256 = sha))
    }
    private fun verifiedOpener(block: suspend (SourceLease) -> ReaderSession) = object : BookOpener {
        override suspend fun open(source: BookSource, format: BookFormat): Result<ReaderSession> = error("Must use acquired lease")
        override suspend fun open(source: SourceLease, format: BookFormat): Result<ReaderSession> =
            try { Result.success(block(source)) } finally { source.close() }
    }
    @Test fun verifiedExternalCoverIsReusedWithoutReextractingOrOrphaningIt() = runTest {
        external()
        val cached = files.writeCover("folder-cached", byteArrayOf(1, 2, 3))
        db.libraryDao().update(db.libraryDao().findById("a")!!.copy(coverPath = cached.path))
        var extractions = 0
        metadata = SourceMetadata { _, _ -> extractions++; Result.success(ExtractedMetadata("Fresh title", null, byteArrayOf(4))) }
        assertTrue(viewModel("a", verifiedOpener { FakeSession(BookFormat.EPUB) }).awaitSettled() is ReaderUiState.Ready)
        assertEquals(0, extractions)
        assertEquals(cached.path, library.book("a")!!.coverPath)
        assertEquals(1, files.coversDir.listFiles()!!.size)
    }

    @Test fun changedBytes_neverOpenOrApplySavedLocatorAndRetainOriginalProgress() = runTest {
        external("old"); library.saveProgress("a", "old-locator", .4)
        var callsBeforeVerification = 0
        val vm = viewModel("a", verifiedOpener { callsBeforeVerification++; FakeSession(BookFormat.EPUB) })
        val changed = vm.awaitSettled() as ReaderUiState.Changed
        assertTrue(changed.newBookId != "a")
        assertEquals("old-locator", library.savedLocator("a"))
        assertEquals(null, library.savedLocator(changed.newBookId))
        assertEquals(null, vm.latestLocatorJson)
        assertEquals(0, callsBeforeVerification)
    }
    @Test fun verifiedSnapshot_isPassedToOpenerBeforeSavedLocatorApplied() = runTest {
        external(); library.saveProgress("a", "old-locator", .4)
        val vm = viewModel("a", verifiedOpener { lease ->
            assertEquals(lease.fingerprint(), library.book("a")!!.sha256)
            assertEquals(null, viewModels.last().latestLocatorJson)
            FakeSession(BookFormat.EPUB)
        })
        val ready = vm.awaitSettled() as ReaderUiState.Ready
        assertEquals("old-locator", ready.initialLocatorJson)
        assertEquals("Dune", ready.title) // Metadata failures retain filename/title fallback.
    }
    @Test fun changedProviderDuringOpen_closesSessionWithoutApplyingLocator() = runTest {
        external(); stamp = SourceStamp(0, 1)
        library.saveProgress("a", "old-locator", .4)
        val opened = FakeSession(BookFormat.EPUB)
        val vm = viewModel("a", verifiedOpener { stamp = SourceStamp(0, 2); opened })
        assertEquals(ReaderUiState.Reconnect, vm.awaitSettled())
        assertTrue(opened.closed)
        assertEquals(null, vm.latestLocatorJson)
        assertEquals("old-locator", library.savedLocator("a"))
    }
    @Test fun changedDuringFingerprint_doesNotVerifyOrUseOldLocator() = runTest {
        external(); stamp = SourceStamp(0, 1)
        duringAcquire = { stamp = SourceStamp(0, 2) }
        var opens = 0
        val vm = viewModel("a", verifiedOpener { opens++; FakeSession(BookFormat.EPUB) })
        assertEquals(ReaderUiState.Reconnect, vm.awaitSettled())
        assertEquals(null, library.book("a")!!.sha256)
        assertEquals(0, opens)
    }
    @Test fun deniedSource_fallsBackToHealthyVerifiedSource() = runTest {
        external(); deniedUris = setOf("content://test/document/a")
        db.sourceDao().insert(BookSourceEntity("z", "a", "EXTERNAL", uri = "content://test/document/z",
            authority = "test", documentId = "z"))
        assertTrue(viewModel("a", verifiedOpener { FakeSession(BookFormat.EPUB) }).awaitSettled() is ReaderUiState.Ready)
    }
    @Test fun reconnectSameBytes_usesSavedLocatorAfterVerification() = runTest {
        external(java.security.MessageDigest.getInstance("SHA-256").digest(bookFile.readBytes()).joinToString("") { "%02x".format(it) })
        library.saveProgress("a", "old-locator", .4)
        deniedUris = setOf("content://test/document/a")
        val vm = viewModel("a", verifiedOpener { FakeSession(BookFormat.EPUB) })
        assertEquals(ReaderUiState.Reconnect, vm.awaitSettled())
        vm.reconnect(BookSource.External("content://test/document/z", DocumentKey("test", "z")))
        assertEquals("old-locator", (vm.awaitSettled() as ReaderUiState.Ready).initialLocatorJson)
    }
    @Test fun reconnectSameDocumentThroughNewGrant_freshOpenRetainsPosition() = runTest {
        external(java.security.MessageDigest.getInstance("SHA-256").digest(bookFile.readBytes()).joinToString("") { "%02x".format(it) })
        library.saveProgress("a", "old-locator", .4)
        deniedUris = setOf("content://test/document/a")
        val replacement = BookSource.External("content://test/tree/new/document/a", DocumentKey("test", "a"))
        val originalId = db.sourceDao().forBook("a").single().id
        val vm = viewModel("a", verifiedOpener { FakeSession(BookFormat.EPUB) })
        assertEquals(ReaderUiState.Reconnect, vm.awaitSettled())
        vm.reconnect(replacement)
        assertEquals("old-locator", (vm.awaitSettled() as ReaderUiState.Ready).initialLocatorJson)
        assertEquals(originalId, db.sourceDao().forBook("a").single().id)
        val fresh = viewModel("a", verifiedOpener { FakeSession(BookFormat.EPUB) })
        assertEquals("old-locator", (fresh.awaitSettled() as ReaderUiState.Ready).initialLocatorJson)
        assertEquals(listOf(replacement), sources.sources("a"))
    }
    @Test fun hiddenOwnedMatch_requiresRestoreBeforeRedirect() = runTest {
        external()
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bookFile.readBytes()).joinToString("") { "%02x".format(it) }
        db.libraryDao().insert(BookEntity("hidden", "Hidden", null, "EPUB", "/old-missing", 0, sha, null, null,
            "READING", 0, 1, 1, 2))
        val vm = viewModel("a", verifiedOpener { error("Hidden content cannot open") })
        assertEquals(ReaderUiState.HiddenMatch("hidden"), vm.awaitSettled())
        vm.restoreHiddenMatch()
        assertEquals(ReaderUiState.Redirect("hidden"), withContext(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.withTimeout(2_000) { vm.state.first { it is ReaderUiState.Redirect } } })
        assertEquals(null, db.libraryDao().findIncludingHidden("hidden")!!.deletedAt)
    }

    @Test fun reconnectHiddenReplacement_restoresAndOpensWithoutPickingAgain() = runTest {
        external()
        library.saveProgress("a", "original-anchor", .4) // A read conflict keeps its own identity.
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bookFile.readBytes()).joinToString("") { "%02x".format(it) }
        db.libraryDao().insert(BookEntity("hidden", "Hidden", null, "EPUB", null, 0, sha, null, null,
            "READING", 0, 1, 1, 2))
        deniedUris = setOf("content://test/document/a")
        var opens = 0
        val vm = viewModel("a", verifiedOpener { opens++; FakeSession(BookFormat.EPUB) })
        assertEquals(ReaderUiState.Reconnect, vm.awaitSettled())
        val replacement = BookSource.External("content://test/document/replacement", DocumentKey("test", "replacement"))
        vm.reconnect(replacement)
        assertEquals(ReaderUiState.HiddenMatch("hidden"), vm.awaitSettled())
        vm.restoreHiddenMatch()
        val settled = withContext(kotlinx.coroutines.Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(2_000) { vm.state.first { it is ReaderUiState.Ready || it is ReaderUiState.Reconnect } }
        }
        assertTrue("Restore must continue with the selected replacement", settled is ReaderUiState.Ready)
        assertEquals("original-anchor", (settled as ReaderUiState.Ready).initialLocatorJson)
        assertTrue(replacement in sources.sources("a"))
        assertEquals(1, opens)
    }

    @Test fun doubleRestoreWhileRoomWriteIsBlocked_publishesOneSessionAndClosesItExactlyOnce() = runTest {
        external()
        library.saveProgress("a", "original-anchor", .4)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bookFile.readBytes()).joinToString("") { "%02x".format(it) }
        db.libraryDao().insert(BookEntity("hidden", "Hidden", null, "EPUB", null, 0, sha, null, null,
            "READING", 0, 1, 1, 2))
        class CountedSession : ReaderSession {
            override val format = BookFormat.EPUB
            var closeCalls = 0
            override fun close() { closeCalls++ }
        }
        val opened = java.util.concurrent.CopyOnWriteArrayList<CountedSession>()
        val vm = viewModel("a", verifiedOpener { CountedSession().also { opened.add(it) } })
        assertEquals(ReaderUiState.HiddenMatch("hidden"), vm.awaitSettled())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch(kotlinx.coroutines.Dispatchers.IO) {
            db.withWriteTransaction { entered.complete(Unit); release.await() }
        }
        entered.await()
        val existingJobs = vm.viewModelScope.coroutineContext[Job]!!.children.toSet()
        try {
            vm.restoreHiddenMatch()
            val stateDuringWrite = vm.state.value
            vm.restoreHiddenMatch()
            val restoreJobs = vm.viewModelScope.coroutineContext[Job]!!.children.filter { it !in existingJobs }.toList()
            release.complete(Unit)
            writer.join()
            restoreJobs.joinAll()
            assertTrue(vm.state.value is ReaderUiState.Ready)
            assertEquals("Repeated Restore must not construct a second live session", 1, opened.size)
            assertEquals(ReaderUiState.Loading, stateDuringWrite)
            assertEquals(0, opened.single().closeCalls)
            viewModelStore.clear()
            assertEquals(1, opened.single().closeCalls)
        } finally { release.complete(Unit); writer.join() }
    }

    @Test fun metadataUsesTheSameLeaseAndStorageFailureDoesNotBlockReading() = runTest {
        external()
        var borrowed: SourceLease? = null
        metadata = SourceMetadata { lease, _ -> borrowed = lease; Result.success(ExtractedMetadata("Enriched", "Author", byteArrayOf(1))) }
        files.coversDir.parentFile!!.mkdirs()
        files.coversDir.writeText("Prevent directory creation")
        val vm = viewModel("a", verifiedOpener { lease ->
            assertTrue(borrowed === lease)
            FakeSession(BookFormat.EPUB)
        })
        assertTrue(vm.awaitSettled() is ReaderUiState.Ready)
    }

    @Test fun cancelledOpenReturningSession_closesExactlyOnce() = runTest {
        external()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var closeCalls = 0
        val opened = object : ReaderSession {
            override val format = BookFormat.EPUB
            override fun close() { closeCalls++ }
        }
        val vm = viewModel("a", verifiedOpener {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            opened
        })
        entered.await()
        vm.viewModelScope.cancel()
        release.complete(Unit)
        vm.viewModelScope.coroutineContext[Job]!!.join()
        assertEquals(1, closeCalls)
        assertEquals(null, vm.latestLocatorJson)
    }

    @Test fun toggleOverlay_flips() = runTest {
        val vm = viewModel("a")
        vm.awaitSettled()
        val before = vm.overlayVisible.value
        vm.toggleOverlay()
        assertEquals(!before, vm.overlayVisible.value)
    }
}
