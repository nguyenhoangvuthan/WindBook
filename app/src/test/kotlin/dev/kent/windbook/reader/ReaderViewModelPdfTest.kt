package dev.kent.windbook.reader

import android.graphics.Bitmap
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.BookSourcesRepository
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceMetadata
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.BookPreferencesEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.EffectiveDisplay
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.reader.pdf.PdfPageSource
import dev.kent.windbook.reader.pdf.PixelRegion
import dev.kent.windbook.testing.MainDispatcherRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderViewModelPdfTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var library: LibraryRepository
    private lateinit var prefs: DisplayPreferencesRepository

    private class FakePdf(private val size: PageSize, override val pageCount: Int = 10, val fingerprint: String? = "sha") : PdfSession {
        override suspend fun annotationFingerprint() = fingerprint
        override val format = BookFormat.PDF
        override val pages = object : PdfPageSource {
            override val pageCount = 10
            override fun pageSize(page: Int) = size
            override suspend fun measure(page: Int) = size
            override suspend fun cropPlan() = CropPlan.NONE
            override suspend fun pageCrop(page: Int, plan: CropPlan) = CropBox.FULL
            override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap =
                Bitmap.createBitmap(widthPx, widthPx, Bitmap.Config.ARGB_8888)
        }
        override suspend fun firstPageSizes() = List(5) { size }
        override fun locatorJsonFor(page: Int) = """{"href":"x.pdf","type":"application/pdf","locations":{"position":${page + 1}}}"""
        override fun pageOf(locatorJson: String?): Int =
            Regex("\"position\":(\\d+)").find(locatorJson ?: "")?.groupValues?.get(1)?.toInt()?.minus(1) ?: 0
        override fun close() = Unit
    }

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        library = LibraryRepository(db.libraryDao(), BookFileStore(tmp.newFolder("app")))
        prefs = DisplayPreferencesRepository(db.bookPreferencesDao())
        val file = tmp.newFile("a.pdf")
        db.libraryDao().insert(BookEntity("a", "Doc", null, "PDF", file.absolutePath, 1, "sha", null, null, "TO_READ", 1, null, 1, null))
    }

    @After fun tearDown() = db.close()

    private fun model(opener: BookOpener) = ReaderViewModel("a", library, opener, prefs,
        BookSourcesRepository(db, FolderRepository(db, { emptySet() }, {}, { _, _ -> })),
        SourceAccess(ApplicationProvider.getApplicationContext()), SourceMetadata { _, _ -> error("Owned metadata") },
        BookFileStore(tmp.root))
    private fun vm(size: PageSize) = model(BookOpener { _, _ -> Result.success(FakePdf(size)) })

    @Test fun annotationRevealRequiresFingerprintOfTheOpenedSession() = runTest {
        val model=vm(PageSize(400f,600f))
        val ready=model.state.first { it is ReaderUiState.Ready } as ReaderUiState.Ready
        assertEquals("sha",model.annotationFingerprint(ready.session as PdfSession))
        val changed=model(BookOpener { _,_->Result.success(FakePdf(PageSize(400f,600f),fingerprint="changed")) })
        val opened=changed.state.first { it is ReaderUiState.Ready } as ReaderUiState.Ready
        org.junit.Assert.assertNull(changed.annotationFingerprint(opened.session as PdfSession))
        org.junit.Assert.assertNull(model.annotationFingerprint(opened.session))
    }

    @Test fun fitPageWritesCombinedChoicePreservesVerticalZoomAndRepeatsWithoutMovingPage() = runTest {
        val dao = db.bookPreferencesDao()
        dao.upsert(BookPreferencesEntity("a", """{"pdfLayout":"VERTICAL","cropMargins":true,"pdfZoom":2,"pdfZoomX":0.5,"future":"keep"}""", 1))
        val writes = mutableListOf<BookPreferencesEntity>()
        prefs = DisplayPreferencesRepository(object : dev.kent.windbook.core.data.db.BookPreferencesDao by dao {
            override suspend fun upsert(entity: BookPreferencesEntity) {
                writes.add(entity)
                dao.upsert(entity)
            }
        })
        val model = vm(PageSize(400f, 600f))
        model.pdfDisplay.filterNotNull().first()
        model.onPdfPageChanged(4)
        val beforeLocator = model.latestLocatorJson
        model.toggleOverlay()
        model.fitPdfPage()
        model.pdfFitRequestId.first { it == 1L }
        assertEquals(false, model.overlayVisible.value)
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false, 2f, .5f), prefs.observe("a").first { it.pdfLayout == PdfLayout.PAGED })
        assertEquals(1, writes.size)
        assertEquals("keep", org.json.JSONObject(dao.json("a")!!).getString("future"))
        assertEquals(beforeLocator, model.latestLocatorJson)
        assertEquals(4, model.initialPdfPage())
        model.pdfDisplay.filterNotNull().first { it.layout == PdfLayout.PAGED && !it.cropMargins }
        model.fitPdfPage()
        model.pdfFitRequestId.first { it == 2L }
        assertEquals(2, writes.size)
        assertEquals(beforeLocator, model.latestLocatorJson)
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false, 2f, .5f), prefs.observe("a").first())
    }

    @Test fun portraitPdfDefaultsToPagedWithoutCrop() = runTest {
        val display = vm(PageSize(400f, 600f)).pdfDisplay.filterNotNull().first()
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), display)
    }

    @Test fun slidesDefaultToPagedWithoutCrop() = runTest {
        val display = vm(PageSize(720f, 540f)).pdfDisplay.filterNotNull().first()
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), display)
    }

    @Test fun choosingPagedIsSavedForTheBook() = runTest {
        val model = vm(PageSize(400f, 600f))
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), model.pdfDisplay.filterNotNull().first())
        model.setPdfLayout(PdfLayout.PAGED)
        // The effective layout is unchanged; verify the explicit persisted choice.
        val saved = prefs.observe("a").first { it.pdfLayout == PdfLayout.PAGED }
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, null), saved)
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), vm(PageSize(400f, 600f)).pdfDisplay.filterNotNull().first())
    }

    @Test fun pageChangeSavesLocatorAndProgress() = runTest {
        val model = vm(PageSize(400f, 600f))
        model.pdfDisplay.filterNotNull().first()
        model.onPdfPageChanged(4)
        assertEquals(0.5, model.progression.value, 1e-9) // page 5 of 10
        library.savedLocatorFlowFirst("a") { it?.contains("\"position\":5") == true }
    }

    @Test fun newViewModelReopensLastPdfPage() = runTest {
        val first = vm(PageSize(400f, 600f))
        first.pdfDisplay.filterNotNull().first()
        first.onPdfPageChanged(7)
        library.savedLocatorFlowFirst("a") { it?.contains("\"position\":8") == true }

        val second = vm(PageSize(400f, 600f))
        second.pdfDisplay.filterNotNull().first()
        assertEquals(7, second.initialPdfPage())
    }

    /** Review #6: a page that fails while sizing the first pages must show "can't open", not crash the app. */
    @Test fun brokenFirstPagesShowCannotOpenInsteadOfCrashing() = runTest {
        val broken = object : PdfSession by FakePdf(PageSize(400f, 600f)) {
            override suspend fun firstPageSizes(): List<PageSize> = throw IllegalStateException("bad page")
        }
        val model = model(BookOpener { _, _ -> Result.success(broken) })
        assertEquals(ReaderUiState.Error(ReaderError.CANNOT_OPEN), model.state.first { it !is ReaderUiState.Loading })
    }

    /** Saving hops to Room's IO dispatcher; poll in real time (runTest's virtual clock would skip the waits). */
    private suspend fun LibraryRepository.savedLocatorFlowFirst(id: String, matches: (String?) -> Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repeat(200) {
                if (matches(savedLocator(id))) return@withContext
                kotlinx.coroutines.delay(10)
            }
            throw AssertionError("locator never saved: ${savedLocator(id)}")
        }

    @Test fun zoomIsSavedForTheBookAndRestored() = runTest {
        val first = vm(PageSize(400f, 600f))
        first.pdfDisplay.filterNotNull().first()
        first.setPdfZoom(2f, 0.4f)
        prefs.observe("a").first { it.zoom == 2f }

        val second = vm(PageSize(400f, 600f))
        val display = second.pdfDisplay.filterNotNull().first { it.zoom == 2f }
        assertEquals(0.4f, display.zoomX, 1e-6f)
    }

    @Test fun legacyReflowBookReopensVerticallyAtTheSavedPageEvenForSlides() = runTest {
        val first = vm(PageSize(720f, 540f))
        first.pdfDisplay.filterNotNull().first()
        first.onPdfPageChanged(7)
        library.savedLocatorFlowFirst("a") { it?.contains("\"position\":8") == true }
        db.bookPreferencesDao().upsert(BookPreferencesEntity(
            "a", """{"pdfLayout":"REFLOW","pdfReflowSize":5,"pdfZoom":2.0,"pdfZoomX":0.4}""", 1,
        ))

        val reopened = vm(PageSize(720f, 540f))
        val display = reopened.pdfDisplay.filterNotNull().first()
        assertEquals(PdfLayout.VERTICAL, display.layout)
        assertEquals(2f, display.zoom, 1e-6f)
        assertEquals(0.4f, display.zoomX, 1e-6f)
        assertEquals(7, reopened.initialPdfPage())
    }
}
