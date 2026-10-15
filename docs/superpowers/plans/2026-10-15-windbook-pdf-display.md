# WindBook PDF Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** PDFs open fitted to the phone. Portrait books scroll vertically at full width with white margins auto-cropped, landscape slides and comics read page by page, each book remembers its choice, and a 24 MB scan opens in ≤ 1.5 s under 400 MB. EPUB text stays clear of screen edges, and landscape covers show whole.

**Architecture:** WindBook renders PDF pages itself with the framework `PdfRenderer` on one dedicated thread (open page → render through a crop `Matrix` → close page) behind a `PdfPageSource` interface in `:reader`. Margin cropping is pure Kotlin in `:core:model` (`MarginDetector`) fed with 300 px luminance renders. A Compose `PdfReaderView` in `:app` shows the pages in a `LazyColumn` (vertical) or `HorizontalPager` (paged). Per-book choices live in a new Room table `book_preferences` (schema v2, auto-migration). Readium still opens the publication for metadata and `positions()`, so saved PDF locators keep the M1 format.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00 (foundation `LazyColumn`, `HorizontalPager`, pointer input), `android.graphics.pdf.PdfRenderer`, Room 3.0.3 (`AutoMigration`), Readium 3.4.0 (publication + positions only for PDF), Hilt, JUnit 4, Robolectric 4.17, Compose UI test, AndroidX Test on emulator `Neo3GT_API_36`.

**Spec:** `docs/superpowers/specs/2026-10-15-windbook-pdf-display-design.md`

## Global Constraints

- minSdk 31, targetSdk 36, compileSdk 37. Bytecode target Java 11. Package root `dev.kent.windbook`.
- **No new Gradle dependency.** The PDF renderer is `android.graphics.pdf.PdfRenderer`. PdfiumAndroid is not called from WindBook code.
- All `PdfRenderer` calls run on one single-thread dispatcher. Exactly one page is open at a time, and every page is closed right after it renders.
- Page bitmaps are `ARGB_8888`, filled white before rendering. The bitmap cache is bounded at min(48 MB, maxMemory / 6).
- Saved PDF locators keep the M1 JSON format (`publication.positions()[i]`). Progress = `readingProgression(position = i + 1, positionCount = pageCount, totalProgression = null)`.
- Defaults:
  - **Layout:** median aspect of pages 1–5; > 1 means `PAGED`, otherwise `VERTICAL`.
  - **Crop:** on for `VERTICAL`, off for `PAGED`.
  - **Crop math:** ink < 200/255. A row/column counts if it has ≥ max(2, 0.3 % of the line) ink pixels. Padding 1.5 %. At most 25 % cropped per side. Samples narrower than 40 % are ignored. Up to 12 samples per parity, skipping the first 4 and the last 2 pages (all pages when ≤ 8). Book box = 10th/90th percentiles. A page's own box is unioned in.
- `book_preferences(book_id TEXT PK → books.id ON DELETE CASCADE, json TEXT NOT NULL, updated_at INTEGER NOT NULL)`. JSON shape: `{"pdfLayout":…,"cropMargins":…}`. Unknown keys are preserved.
- Every user-visible string goes in both `values/strings.xml` and `values-vi/strings.xml`.
- Commits use conventional prefixes. **No `Co-Authored-By` trailer** (owner rule).
- CI check: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`. Device check: `./gradlew :reader:connectedDebugAndroidTest` on `Neo3GT_API_36` (README §4A).

## Review Focus

1. **A PDF saved by M1's Readium navigator** (locator JSON with `"position":21,"fragments":["page=21"]`) reopens on page 21. Test: `PdfLocatorsTest.m1LocatorReopensSamePage` (Task 4).
2. **A portrait book with one landscape figure page** lays that page out at its own aspect without crashing or overlapping neighbours. Test: `PdfReaderViewTest.mixedPageSizesLayOutIndependently` (Task 7).
3. **A corrupt or password-protected PDF** shows the existing "can't be opened" screen instead of crashing. Test: `PdfEngineDeviceTest.corruptPdfFailsToOpen` (Task 4).
4. **Comic pages and full-bleed images** are never cropped into their content. Test: `MarginDetectorTest.fullBleedImageIsNotCropped` (Task 1).
5. **Rotation or process death mid-PDF** reopens on the same page, not page 1. Test: `ReaderViewModelPdfTest.newViewModelReopensLastPdfPage` (Task 5).

---

## File Structure

```
core/model/src/main/kotlin/dev/kent/windbook/core/model/
    PdfDisplay.kt          PdfLayout, PageSize, BookDisplayPrefs, EffectiveDisplay, defaultPdfLayout, resolve   (Task 1)
    MarginDetector.kt      CropBox, CropPlan, MarginDetector (contentBox, bookBox, forPage, sampleIndices)      (Task 1)
core/data/src/main/kotlin/dev/kent/windbook/core/data/
    db/Entities.kt         + BookPreferencesEntity                                                             (Task 2)
    db/BookPreferencesDao.kt                                                                                    (Task 2)
    db/WindBookDatabase.kt version 2, AutoMigration(1,2), open(context, path, driver)                           (Task 2)
    DisplayPreferencesRepository.kt   + BookPreferencesCodec                                                    (Task 2)
core/data/schemas/…/2.json  generated by Room                                                                   (Task 2)
reader/src/main/kotlin/dev/kent/windbook/reader/
    pdf/PdfDocumentHandle.kt   PdfRenderer on one thread, render(page, crop, widthPx, region), luminance()       (Task 3)
    pdf/PdfPageSource.kt       interface + PixelRegion                                                           (Task 3)
    pdf/PdfPages.kt            PdfPageSource impl: LRU cache, crop plan, own boxes                               (Task 4)
    pdf/PdfReaderSession.kt    PdfSession impl + pageIndexOf                                                     (Task 4)
    ReaderApi.kt               + PdfSession interface                                                            (Task 4)
    ReaderEngine.kt            PDF → PdfReaderSession                                                            (Task 4)
    ReaderHostFragment.kt      EPUB only                                                                          (Task 4)
    ReaderTheme.kt             + epubPageBackground(darkTheme)                                                   (Task 8)
app/src/main/kotlin/dev/kent/windbook/
    reader/ReaderViewModel.kt  display prefs, onPdfPageChanged, initialPdfPage                                    (Task 5)
    di/AppModule.kt            + DisplayPreferencesRepository                                                    (Task 5)
    reader/ReaderScreen.kt     overlay View menu (Task 6); PDF/EPUB wiring + EPUB edge padding (Task 8)
    reader/pdf/PdfReaderView.kt  vertical + paged Compose views                                                  (Task 7)
    ui/icons/WindBookIcons.kt  + ViewMode                                                                         (Task 6)
    ui/components/BookCover.kt landscape covers fit whole                                                        (Task 8)
```

---

### Task 1: Display model and margin detector (pure Kotlin)

**Files:**
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/PdfDisplay.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/MarginDetector.kt`
- Test: `core/model/src/test/kotlin/dev/kent/windbook/core/model/PdfDisplayTest.kt`, `core/model/src/test/kotlin/dev/kent/windbook/core/model/MarginDetectorTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum class PdfLayout { VERTICAL, PAGED }`
  - `data class PageSize(val width: Float, val height: Float) { val aspect: Float }`
  - `data class BookDisplayPrefs(val pdfLayout: PdfLayout? = null, val cropMargins: Boolean? = null)`
  - `data class EffectiveDisplay(val layout: PdfLayout, val cropMargins: Boolean)`
  - `fun defaultPdfLayout(firstPages: List<PageSize>): PdfLayout`
  - `fun BookDisplayPrefs.resolve(firstPages: List<PageSize>): EffectiveDisplay`
  - `data class CropBox(left, top, right, bottom: Float)` with `width`, `height`, `union()`, `clamped()`, `CropBox.FULL`
  - `data class CropPlan(val odd: CropBox, val even: CropBox) { fun boxFor(pageIndex: Int): CropBox }` (index 0 = page 1 = odd)
  - `object MarginDetector { fun contentBox(luma: ByteArray, width: Int, height: Int): CropBox?; fun bookBox(samples: List<CropBox?>): CropBox; fun forPage(book: CropBox, own: CropBox?): CropBox; fun sampleIndices(pageCount: Int, perParity: Int = 12): List<Int> }`

- [ ] **Step 1: Write the failing tests**

`core/model/src/test/kotlin/dev/kent/windbook/core/model/PdfDisplayTest.kt`:

```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfDisplayTest {
    private val portrait = PageSize(504f, 657f)
    private val slide = PageSize(720f, 540f)

    @Test fun portraitBooksScrollVertically() =
        assertEquals(PdfLayout.VERTICAL, defaultPdfLayout(List(5) { portrait }))

    @Test fun slidesArePaged() = assertEquals(PdfLayout.PAGED, defaultPdfLayout(List(5) { slide }))

    @Test fun oneLandscapeCoverDoesNotFlipAPortraitBook() =
        assertEquals(PdfLayout.VERTICAL, defaultPdfLayout(listOf(slide, portrait, portrait, portrait, portrait)))

    @Test fun noPagesFallsBackToVertical() = assertEquals(PdfLayout.VERTICAL, defaultPdfLayout(emptyList()))

    @Test fun cropDefaultsFollowTheLayout() {
        assertEquals(EffectiveDisplay(PdfLayout.VERTICAL, true), BookDisplayPrefs().resolve(listOf(portrait)))
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), BookDisplayPrefs().resolve(listOf(slide)))
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), BookDisplayPrefs(pdfLayout = PdfLayout.PAGED).resolve(listOf(portrait)))
    }

    @Test fun explicitChoicesWin() =
        assertEquals(
            EffectiveDisplay(PdfLayout.PAGED, true),
            BookDisplayPrefs(PdfLayout.PAGED, cropMargins = true).resolve(listOf(portrait)),
        )
}
```

`core/model/src/test/kotlin/dev/kent/windbook/core/model/MarginDetectorTest.kt`:

```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarginDetectorTest {
    private val w = 300
    private val h = 400

    /** White page (255) with black (0) filled where [ink] is true. */
    private fun page(ink: (x: Int, y: Int) -> Boolean) = ByteArray(w * h) { i ->
        if (ink(i % w, i / w)) 0 else 255.toByte()
    }

    private fun assertBox(expected: CropBox, actual: CropBox?, tol: Float = 0.01f) {
        actual!!
        assertEquals(expected.left, actual.left, tol)
        assertEquals(expected.top, actual.top, tol)
        assertEquals(expected.right, actual.right, tol)
        assertEquals(expected.bottom, actual.bottom, tol)
    }

    @Test fun textBlockGetsPaddedBox() {
        val luma = page { x, y -> x in 60 until 240 && y in 80 until 320 }
        // 60/300 = 0.20, 80/400 = 0.20, 240/300 = 0.80, 320/400 = 0.80, each padded by 0.015.
        assertBox(CropBox(0.185f, 0.185f, 0.815f, 0.815f), MarginDetector.contentBox(luma, w, h))
    }

    @Test fun isolatedScanSpecksAreIgnored() {
        val luma = page { x, y -> (x in 60 until 240 && y in 80 until 320) || (x == 5 && y == 5) || (x == 290 && y == 390) }
        assertBox(CropBox(0.185f, 0.185f, 0.815f, 0.815f), MarginDetector.contentBox(luma, w, h))
    }

    @Test fun blankPageHasNoBox() = assertNull(MarginDetector.contentBox(page { _, _ -> false }, w, h))

    @Test fun fullBleedImageIsNotCropped() {
        val luma = page { _, _ -> true }
        assertBox(CropBox.FULL, MarginDetector.forPage(MarginDetector.bookBox(listOf(MarginDetector.contentBox(luma, w, h))), null))
    }

    @Test fun cropNeverExceedsAQuarterPerSide() {
        val tiny = CropBox(0.45f, 0.45f, 0.55f, 0.55f)
        val box = tiny.clamped()
        assertEquals(0.25f, box.left, 1e-6f)
        assertEquals(0.75f, box.right, 1e-6f)
    }

    @Test fun bookBoxUsesWideEdgesAndSkipsNearBlankSamples() {
        val samples = listOf(
            CropBox(0.10f, 0.10f, 0.90f, 0.90f),
            CropBox(0.12f, 0.11f, 0.88f, 0.89f),
            CropBox(0.11f, 0.12f, 0.89f, 0.88f),
            CropBox(0.48f, 0.48f, 0.52f, 0.52f), // near blank: ignored
            null,
        )
        val box = MarginDetector.bookBox(samples)
        assertTrue(box.left <= 0.11f && box.right >= 0.89f)
        assertTrue(box.top <= 0.11f && box.bottom >= 0.89f)
    }

    @Test fun noUsableSamplesMeansNoCrop() = assertEquals(CropBox.FULL, MarginDetector.bookBox(listOf(null)))

    @Test fun aWideFigurePageGrowsBeyondTheBookBox() {
        val book = CropBox(0.15f, 0.12f, 0.85f, 0.88f)
        val figure = CropBox(0.05f, 0.30f, 0.95f, 0.60f)
        assertEquals(CropBox(0.05f, 0.12f, 0.95f, 0.88f), MarginDetector.forPage(book, figure))
    }

    @Test fun mirroredMarginsGetOneBoxPerParity() {
        val plan = CropPlan(odd = CropBox(0.20f, 0.1f, 0.90f, 0.9f), even = CropBox(0.10f, 0.1f, 0.80f, 0.9f))
        assertEquals(0.20f, plan.boxFor(0).left) // page 1
        assertEquals(0.10f, plan.boxFor(1).left) // page 2
    }

    @Test fun samplingSkipsFrontMatterAndSpreadsPerParity() {
        val indices = MarginDetector.sampleIndices(pageCount = 300)
        assertTrue(indices.all { it in 4 until 298 })
        assertEquals(12, indices.count { it % 2 == 0 })
        assertEquals(12, indices.count { it % 2 == 1 })
        assertEquals(indices.sorted(), indices)
    }

    @Test fun shortDocumentsSampleEveryPage() = assertEquals(listOf(0, 1, 2), MarginDetector.sampleIndices(3))
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:model:test --tests 'dev.kent.windbook.core.model.PdfDisplayTest' --tests 'dev.kent.windbook.core.model.MarginDetectorTest'`
Expected: compilation FAILS (`Unresolved reference: defaultPdfLayout`, `MarginDetector`).

- [ ] **Step 3: Implement `PdfDisplay.kt`**

```kotlin
package dev.kent.windbook.core.model

enum class PdfLayout { VERTICAL, PAGED }

/** Page size in PDF points. */
data class PageSize(val width: Float, val height: Float) {
    val aspect: Float get() = width / height
}

/** Per-book display choices; null means "use the default". */
data class BookDisplayPrefs(val pdfLayout: PdfLayout? = null, val cropMargins: Boolean? = null)

data class EffectiveDisplay(val layout: PdfLayout, val cropMargins: Boolean)

/** Landscape books (slides) read page by page; everything else scrolls. Median of pages 1–5. */
fun defaultPdfLayout(firstPages: List<PageSize>): PdfLayout {
    if (firstPages.isEmpty()) return PdfLayout.VERTICAL
    val aspects = firstPages.take(5).map { it.aspect }.sorted()
    return if (aspects[aspects.size / 2] > 1f) PdfLayout.PAGED else PdfLayout.VERTICAL
}

/** Margins are cropped by default only when scrolling; paged views show the whole page. */
fun BookDisplayPrefs.resolve(firstPages: List<PageSize>): EffectiveDisplay {
    val layout = pdfLayout ?: defaultPdfLayout(firstPages)
    return EffectiveDisplay(layout, cropMargins ?: (layout == PdfLayout.VERTICAL))
}
```

- [ ] **Step 4: Implement `MarginDetector.kt`**

```kotlin
package dev.kent.windbook.core.model

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Part of a page to show, as fractions of its width/height (0..1). */
data class CropBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun union(other: CropBox) =
        CropBox(min(left, other.left), min(top, other.top), max(right, other.right), max(bottom, other.bottom))

    /** Never removes more than [MarginDetector.MAX_CROP_PER_SIDE] of any side. */
    fun clamped(): CropBox {
        val m = MarginDetector.MAX_CROP_PER_SIDE
        return CropBox(left.coerceIn(0f, m), top.coerceIn(0f, m), right.coerceIn(1f - m, 1f), bottom.coerceIn(1f - m, 1f))
    }

    companion object {
        val FULL = CropBox(0f, 0f, 1f, 1f)
    }
}

/** Book-level crop, one box per page parity (printed books mirror their margins). */
data class CropPlan(val odd: CropBox, val even: CropBox) {
    /** [pageIndex] is 0-based, so index 0 is page 1 (odd). */
    fun boxFor(pageIndex: Int): CropBox = if (pageIndex % 2 == 0) odd else even

    companion object {
        val NONE = CropPlan(CropBox.FULL, CropBox.FULL)
    }
}

object MarginDetector {
    const val INK_THRESHOLD = 200
    const val PADDING = 0.015f
    const val MAX_CROP_PER_SIDE = 0.25f
    const val MIN_CONTENT = 0.40f

    /** Content box of a page given as luminance bytes (0 = black, 255 = white), or null when the page is blank. */
    fun contentBox(luma: ByteArray, width: Int, height: Int): CropBox? {
        val rowInk = IntArray(height)
        val colInk = IntArray(width)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if ((luma[row + x].toInt() and 0xFF) < INK_THRESHOLD) {
                    rowInk[y]++
                    colInk[x]++
                }
            }
        }
        val rowMin = max(2, ceil(width * 0.003).toInt())
        val colMin = max(2, ceil(height * 0.003).toInt())
        val top = rowInk.indexOfFirst { it >= rowMin }
        if (top < 0) return null
        val bottom = rowInk.indexOfLast { it >= rowMin }
        val left = colInk.indexOfFirst { it >= colMin }
        val right = colInk.indexOfLast { it >= colMin }
        if (left < 0) return null
        return CropBox(
            (left.toFloat() / width - PADDING).coerceAtLeast(0f),
            (top.toFloat() / height - PADDING).coerceAtLeast(0f),
            ((right + 1).toFloat() / width + PADDING).coerceAtMost(1f),
            ((bottom + 1).toFloat() / height + PADDING).coerceAtMost(1f),
        )
    }

    /** Wide edges (10th/90th percentile) of the usable samples, so a wide page is rarely cut. */
    fun bookBox(samples: List<CropBox?>): CropBox {
        val boxes = samples.filterNotNull().filter { it.width >= MIN_CONTENT && it.height >= MIN_CONTENT }
        if (boxes.isEmpty()) return CropBox.FULL
        fun pct(values: List<Float>, p: Float): Float = values.sorted()[((values.size - 1) * p).roundToInt()]
        return CropBox(
            pct(boxes.map { it.left }, 0.1f),
            pct(boxes.map { it.top }, 0.1f),
            pct(boxes.map { it.right }, 0.9f),
            pct(boxes.map { it.bottom }, 0.9f),
        ).clamped()
    }

    /** The book box, grown to include this page's own content so nothing is ever cut. */
    fun forPage(book: CropBox, own: CropBox?): CropBox = if (own == null) book else book.union(own).clamped()

    /** Up to [perParity] 0-based page indices per parity, evenly spread, skipping covers and back matter. */
    fun sampleIndices(pageCount: Int, perParity: Int = 12): List<Int> {
        val range = if (pageCount <= 8) (0 until pageCount).toList() else (4 until pageCount - 2).toList()
        fun spread(list: List<Int>): List<Int> =
            if (list.size <= perParity) list else List(perParity) { i -> list[i * (list.size - 1) / (perParity - 1)] }
        return (spread(range.filter { it % 2 == 0 }) + spread(range.filter { it % 2 == 1 })).sorted()
    }
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:model:test`
Expected: all `:core:model` tests PASS (16 existing + 6 `PdfDisplayTest` + 11 `MarginDetectorTest`).

- [ ] **Step 6: Commit**

```bash
git add core/model/src
git commit -m "feat(model): PDF display defaults and margin detector"
```

---

### Task 2: Per-book preferences table (Room v2)

**Files:**
- Modify: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/Entities.kt` (+ `BookPreferencesEntity`)
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/BookPreferencesDao.kt`
- Modify: `core/data/src/main/kotlin/dev/kent/windbook/core/data/db/WindBookDatabase.kt`
- Create: `core/data/src/main/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepository.kt`
- Create (generated): `core/data/schemas/dev.kent.windbook.core.data.db.WindBookDatabase/2.json`
- Test: `core/data/src/test/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepositoryTest.kt`, `core/data/src/test/kotlin/dev/kent/windbook/core/data/db/MigrationTest.kt`

**Interfaces:**
- Consumes: `BookDisplayPrefs`, `PdfLayout` (Task 1).
- Produces:
  - `BookPreferencesDao` with `fun observeJson(bookId: String): Flow<String?>`, `suspend fun json(bookId: String): String?`, `suspend fun upsert(entity: BookPreferencesEntity)`
  - `WindBookDatabase.bookPreferencesDao()`, `WindBookDatabase.open(context, path, driver)`
  - `class DisplayPreferencesRepository(dao: BookPreferencesDao, clock: () -> Long = System::currentTimeMillis)` with `fun observe(bookId: String): Flow<BookDisplayPrefs>` and `suspend fun set(bookId: String, prefs: BookDisplayPrefs)`
  - `internal object BookPreferencesCodec { fun decode(json: String?): BookDisplayPrefs; fun encode(previous: String?, prefs: BookDisplayPrefs): String }`

- [ ] **Step 1: Write the failing tests**

`core/data/src/test/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepositoryTest.kt`:

```kotlin
package dev.kent.windbook.core.data

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.BookPreferencesEntity
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.PdfLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DisplayPreferencesRepositoryTest {
    private lateinit var db: WindBookDatabase
    private lateinit var repo: DisplayPreferencesRepository

    @Before fun setUp() = runTest {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        repo = DisplayPreferencesRepository(db.bookPreferencesDao(), clock = { 7L })
        db.libraryDao().insert(BookEntity("b", "Book", null, "PDF", "/f", 1, "sha", null, null, "TO_READ", 1, null, 1, null))
    }

    @After fun tearDown() = db.close()

    @Test fun unknownBookHasDefaults() = runTest {
        assertEquals(BookDisplayPrefs(), repo.observe("b").first())
    }

    @Test fun choicesRoundTrip() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, cropMargins = false))
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false), repo.observe("b").first())
    }

    @Test fun unknownKeysFromLaterVersionsSurviveAWrite() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", """{"fontScale":1.2,"pdfLayout":"VERTICAL"}""", 1))
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, null))
        val json = JSONObject(db.bookPreferencesDao().json("b")!!)
        assertEquals(1.2, json.getDouble("fontScale"), 1e-9)
        assertEquals("PAGED", json.getString("pdfLayout"))
    }

    @Test fun garbageJsonFallsBackToDefaults() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", "{broken", 1))
        assertEquals(BookDisplayPrefs(), repo.observe("b").first())
    }

    @Test fun deletingTheBookDeletesItsPreferences() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.PAGED, null))
        db.libraryDao().purge("b")
        assertNull(db.bookPreferencesDao().json("b"))
    }
}
```

`core/data/src/test/kotlin/dev/kent/windbook/core/data/db/MigrationTest.kt`:

```kotlin
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
        assertEquals("""{"href":"x"}""", db.libraryDao().progress("a")!!.locatorJson)
        db.bookPreferencesDao().upsert(BookPreferencesEntity("a", "{}", 4))
        assertEquals("{}", db.bookPreferencesDao().json("a"))
        db.close()
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests 'dev.kent.windbook.core.data.DisplayPreferencesRepositoryTest' --tests 'dev.kent.windbook.core.data.db.MigrationTest'`
Expected: compilation FAILS (`Unresolved reference: BookPreferencesEntity`, `DisplayPreferencesRepository`, `open`).

- [ ] **Step 3: Entity and DAO**

Append to `Entities.kt`:

```kotlin
@Entity(
    tableName = "book_preferences",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookPreferencesEntity(
    @PrimaryKey @ColumnInfo(name = "book_id") val bookId: String,
    val json: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
```

`core/data/src/main/kotlin/dev/kent/windbook/core/data/db/BookPreferencesDao.kt`:

```kotlin
package dev.kent.windbook.core.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookPreferencesDao {
    @Query("SELECT json FROM book_preferences WHERE book_id = :bookId")
    fun observeJson(bookId: String): Flow<String?>

    @Query("SELECT json FROM book_preferences WHERE book_id = :bookId")
    suspend fun json(bookId: String): String?

    @Upsert
    suspend fun upsert(entity: BookPreferencesEntity)
}
```

- [ ] **Step 4: Database v2**

In `WindBookDatabase.kt`:
- Change the annotation to:

```kotlin
@Database(
    entities = [BookEntity::class, ReadingProgressEntity::class, BookPreferencesEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
```

- Add the `import androidx.room3.AutoMigration` import.
- Add `abstract fun bookPreferencesDao(): BookPreferencesDao` below `libraryDao()`.
- Replace `create` with `create` + `open`:

```kotlin
        fun create(context: Context): WindBookDatabase =
            open(context, context.getDatabasePath(FILE_NAME).absolutePath, BundledSQLiteDriver())

        /** Opens (and migrates) the database file at [path]; tests pass the framework driver. */
        fun open(context: Context, path: String, driver: SQLiteDriver): WindBookDatabase =
            Room.databaseBuilder<WindBookDatabase>(context = context, name = path)
                .setDriver(driver)
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
```

- [ ] **Step 5: Repository and codec**

`core/data/src/main/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepository.kt`:

```kotlin
package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.BookPreferencesDao
import dev.kent.windbook.core.data.db.BookPreferencesEntity
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.PdfLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class DisplayPreferencesRepository(
    private val dao: BookPreferencesDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(bookId: String): Flow<BookDisplayPrefs> = dao.observeJson(bookId).map(BookPreferencesCodec::decode)

    suspend fun set(bookId: String, prefs: BookDisplayPrefs) {
        val json = BookPreferencesCodec.encode(dao.json(bookId), prefs)
        dao.upsert(BookPreferencesEntity(bookId, json, clock()))
    }
}

/** JSON so M2 can add per-book fields without another migration; unknown keys are kept on write. */
internal object BookPreferencesCodec {
    private const val LAYOUT = "pdfLayout"
    private const val CROP = "cropMargins"

    fun decode(json: String?): BookDisplayPrefs {
        val obj = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return BookDisplayPrefs()
        val layout = obj.optString(LAYOUT).takeIf { it.isNotEmpty() && !obj.isNull(LAYOUT) }
            ?.let { runCatching { PdfLayout.valueOf(it) }.getOrNull() }
        val crop = if (obj.has(CROP) && !obj.isNull(CROP)) obj.optBoolean(CROP) else null
        return BookDisplayPrefs(layout, crop)
    }

    fun encode(previous: String?, prefs: BookDisplayPrefs): String {
        val obj = previous?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        obj.put(LAYOUT, prefs.pdfLayout?.name ?: JSONObject.NULL)
        obj.put(CROP, prefs.cropMargins ?: JSONObject.NULL)
        return obj.toString()
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest`
Expected: all `:core:data` tests PASS (37 existing + 5 repository + 1 migration). The Room plugin writes `core/data/schemas/dev.kent.windbook.core.data.db.WindBookDatabase/2.json`.

- [ ] **Step 7: Commit**

```bash
git add core/data/src core/data/schemas
git commit -m "feat(data): book_preferences table with auto-migration to schema 2"
```

---

### Task 3: PDF document renderer (framework PdfRenderer on one thread)

**Files:**
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPageSource.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfDocumentHandle.kt`
- Create: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfFixtures.kt`
- Test: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfDocumentHandleTest.kt`

**Interfaces:**
- Consumes: `PageSize`, `CropBox`, `CropPlan` (Task 1).
- Produces:
  - `data class PixelRegion(val x: Int, val y: Int, val width: Int, val height: Int)`
  - `interface PdfPageSource { val pageCount: Int; fun pageSize(page: Int): PageSize; suspend fun measure(page: Int): PageSize; suspend fun cropPlan(): CropPlan; suspend fun pageCrop(page: Int, plan: CropPlan): CropBox; suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion? = null): Bitmap }`
  - `fun renderedHeight(size: PageSize, crop: CropBox, widthPx: Int): Int`
  - `class PdfDocumentHandle : AutoCloseable { val pageCount: Int; fun pageSize(page: Int): PageSize; suspend fun measure(page: Int): PageSize; suspend fun render(page, crop, widthPx, region): Bitmap; suspend fun luminance(page: Int, widthPx: Int = 300): Luma; companion object { suspend fun open(file: File): PdfDocumentHandle } }`
  - `class Luma(val width: Int, val height: Int, val values: ByteArray)`

- [ ] **Step 1: PDF fixture and failing device tests**

`reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfFixtures.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File

/** Real, renderable PDFs written with the framework PdfDocument. */
object PdfFixtures {
    /**
     * [pages] pages of [pageW]×[pageH] points with a solid black box from ([boxL],[boxT]) to ([boxR],[boxB]).
     * Odd pages (1, 3, …) are shifted right by [mirrorShift] points to imitate printed margins.
     */
    fun boxed(
        dir: File,
        name: String = "boxed.pdf",
        pages: Int = 10,
        pageW: Int = 400,
        pageH: Int = 600,
        boxL: Float = 100f, boxT: Float = 150f, boxR: Float = 300f, boxB: Float = 450f,
        mirrorShift: Float = 0f,
    ): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
            val shift = if (i % 2 == 0) mirrorShift else 0f
            page.canvas.drawRect(boxL + shift, boxT, boxR + shift, boxB, paint)
            doc.finishPage(page)
        }
        val file = File(dir, name)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
```

`reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfDocumentHandleTest.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.MarginDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfDocumentHandleTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    @Test fun readsPageCountAndSizes() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir, pages = 3)).use { doc ->
            assertEquals(3, doc.pageCount)
            assertEquals(400f, doc.pageSize(0).width, 0.5f)
            assertEquals(600f, doc.measure(2).height, 0.5f)
        }
    }

    @Test fun luminanceFindsTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val l = doc.luminance(0)
            val box = MarginDetector.contentBox(l.values, l.width, l.height)!!
            // Box 100..300 × 150..450 on 400×600 → 0.25..0.75, padded by 0.015.
            assertEquals(0.235f, box.left, 0.02f)
            assertEquals(0.235f, box.top, 0.02f)
            assertEquals(0.765f, box.right, 0.02f)
            assertEquals(0.765f, box.bottom, 0.02f)
        }
    }

    @Test fun croppedRenderFillsTheBitmapWithTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val crop = CropBox(0.25f, 0.25f, 0.75f, 0.75f)
            val bmp = doc.render(0, crop, widthPx = 500)
            assertEquals(500, bmp.width)
            assertEquals(renderedHeight(doc.pageSize(0), crop, 500), bmp.height)
            // Inside the crop everything is the black box, corners included.
            assertEquals(Color.BLACK, bmp.getPixel(5, 5) or 0xFF000000.toInt())
            assertEquals(Color.BLACK, bmp.getPixel(bmp.width - 5, bmp.height - 5) or 0xFF000000.toInt())
        }
    }

    @Test fun fullPageRenderIsWhiteAroundTheBox() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            val bmp = doc.render(0, CropBox.FULL, widthPx = 400)
            assertEquals(Color.WHITE, bmp.getPixel(10, 10))
            assertEquals(Color.BLACK, bmp.getPixel(200, 300))
        }
    }

    @Test fun regionRenderIsATileOfTheZoomedPage() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.boxed(dir)).use { doc ->
            // Page at 800 px wide (2×): the box starts at x = 200, y = 300. Tile 100×100 at (150, 250).
            val tile = doc.render(0, CropBox.FULL, widthPx = 800, region = PixelRegion(150, 250, 100, 100))
            assertEquals(100, tile.width)
            assertEquals(Color.WHITE, tile.getPixel(10, 10)) // (160, 260): outside the box
            assertEquals(Color.BLACK, tile.getPixel(90, 90)) // (240, 340): inside the box
        }
    }

    @Test fun garbageIsRejected() = runBlocking {
        val junk = dir.resolve("junk.pdf").apply { writeText("%PDF-1.4 not really") }
        assertTrue(runCatching { PdfDocumentHandle.open(junk) }.isFailure)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :reader:compileDebugAndroidTestKotlin`
Expected: compilation FAILS (`Unresolved reference: PdfDocumentHandle`, `renderedHeight`, `PixelRegion`).

- [ ] **Step 3: Source interface**

`reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPageSource.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PageSize
import kotlin.math.roundToInt

/** A rectangle in the pixels of a page rendered at some width (used for zoomed tiles). */
data class PixelRegion(val x: Int, val y: Int, val width: Int, val height: Int)

/** Everything the PDF view needs; the app's tests fake it, production uses [PdfPages]. */
interface PdfPageSource {
    val pageCount: Int

    /** Best known size of [page] in points; pages not measured yet report the first page's size. */
    fun pageSize(page: Int): PageSize

    suspend fun measure(page: Int): PageSize

    /** Book-level crop (expensive: renders samples). Call once, off the main thread. */
    suspend fun cropPlan(): CropPlan

    /** The plan's box for [page] grown to this page's own content. */
    suspend fun pageCrop(page: Int, plan: CropPlan): CropBox

    suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion? = null): Bitmap
}

/** Height in pixels of [crop] of a page of [size] rendered [widthPx] wide. */
fun renderedHeight(size: PageSize, crop: CropBox, widthPx: Int): Int =
    (widthPx * (crop.height * size.height) / (crop.width * size.width)).roundToInt().coerceAtLeast(1)
```

- [ ] **Step 4: Document handle**

`reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfDocumentHandle.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.PageSize
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/** Luminance (0 black … 255 white) of a rendered page, row by row. */
class Luma(val width: Int, val height: Int, val values: ByteArray)

/**
 * A PDF opened with the framework [PdfRenderer]. Every call runs on one private thread, and each render
 * opens the page, draws it and closes it again, so memory stays flat even on long scanned books.
 */
class PdfDocumentHandle private constructor(
    private val fd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val thread: ExecutorCoroutineDispatcher,
    firstPage: PageSize,
) : AutoCloseable {

    val pageCount: Int = renderer.pageCount
    private val sizes = ConcurrentHashMap<Int, PageSize>().apply { put(0, firstPage) }
    private val fallback = firstPage

    fun pageSize(page: Int): PageSize = sizes[page] ?: fallback

    suspend fun measure(page: Int): PageSize = sizes[page] ?: withContext(thread) {
        renderer.openPage(page).use { PageSize(it.width.toFloat(), it.height.toFloat()) }.also { sizes[page] = it }
    }

    suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion? = null): Bitmap = withContext(thread) {
        renderer.openPage(page).use { pdfPage ->
            val size = PageSize(pdfPage.width.toFloat(), pdfPage.height.toFloat()).also { sizes[page] = it }
            val scale = widthPx / (crop.width * size.width)
            val bitmap = Bitmap.createBitmap(
                region?.width ?: widthPx,
                region?.height ?: renderedHeight(size, crop, widthPx),
                Bitmap.Config.ARGB_8888,
            ).apply { eraseColor(Color.WHITE) }
            val matrix = Matrix().apply {
                setTranslate(-crop.left * size.width, -crop.top * size.height)
                postScale(scale, scale)
                if (region != null) postTranslate(-region.x.toFloat(), -region.y.toFloat())
            }
            pdfPage.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    suspend fun luminance(page: Int, widthPx: Int = 300): Luma {
        val bitmap = render(page, CropBox.FULL, widthPx)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        val luma = ByteArray(pixels.size) { i ->
            val p = pixels[i]
            ((299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000).toByte()
        }
        return Luma(widthPx, pixels.size / widthPx, luma)
    }

    override fun close() {
        runBlocking(thread) { renderer.close() }
        fd.close()
        thread.close()
    }

    companion object {
        suspend fun open(file: File): PdfDocumentHandle {
            val thread = Executors.newSingleThreadExecutor { Thread(it, "windbook-pdf") }.asCoroutineDispatcher()
            return try {
                withContext(thread) {
                    val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = try {
                        PdfRenderer(fd)
                    } catch (e: Exception) {
                        fd.close()
                        throw e
                    }
                    if (renderer.pageCount == 0) {
                        renderer.close()
                        fd.close()
                        throw java.io.IOException("PDF has no pages")
                    }
                    val first = renderer.openPage(0).use { PageSize(it.width.toFloat(), it.height.toFloat()) }
                    PdfDocumentHandle(fd, renderer, thread, first)
                }
            } catch (e: Exception) {
                thread.close()
                throw e
            }
        }
    }
}
```

- [ ] **Step 5: Run the device tests**

Start `Neo3GT_API_36` if needed (README §4A), then:
Run: `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kent.windbook.reader.pdf.PdfDocumentHandleTest`
Expected: 6 tests PASS.

- [ ] **Step 6: Commit**

```bash
git add reader/src/main/kotlin/dev/kent/windbook/reader/pdf reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf
git commit -m "feat(reader): framework PdfRenderer document handle with crop and tile rendering"
```

---

### Task 4: PDF session, page source and engine wiring

**Files:**
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderApi.kt` (+ `PdfSession`)
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPages.kt`
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderSession.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderEngine.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderHostFragment.kt` (EPUB only)
- Test: `reader/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfLocatorsTest.kt`, `reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfEngineDeviceTest.kt`

**Interfaces:**
- Consumes: `PdfDocumentHandle`, `PdfPageSource`, `PixelRegion` (Task 3). `MarginDetector`, `CropPlan` (Task 1).
- Produces:
  - `interface PdfSession : ReaderSession { val pageCount: Int; val pages: PdfPageSource; suspend fun firstPageSizes(): List<PageSize>; fun locatorJsonFor(page: Int): String; fun pageOf(locatorJson: String?): Int }`
  - `internal fun pageIndexOf(locator: Locator?, pageCount: Int): Int`
  - `class PdfPages(document: PdfDocumentHandle, maxCacheBytes: Int = defaultCacheBytes()) : PdfPageSource`
  - `ReaderEngine.open(file, BookFormat.PDF)` returns a `PdfSession`

- [ ] **Step 1: Write the failing tests**

`reader/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfLocatorsTest.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import dev.kent.windbook.reader.toLocatorOrNull
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfLocatorsTest {
    @Test fun m1LocatorReopensSamePage() {
        // Exactly what M1's Readium PDF navigator saved.
        val json = """{"href":"publication.pdf","type":"application/pdf","locations":{"fragments":["page=21"],"progression":0.0,"position":21,"totalProgression":0.1149}}"""
        assertEquals(20, pageIndexOf(json.toLocatorOrNull(), pageCount = 174))
    }

    @Test fun missingOrBrokenLocatorOpensFirstPage() {
        assertEquals(0, pageIndexOf(null, pageCount = 10))
        assertEquals(0, pageIndexOf("""{"href":"x","type":"application/pdf"}""".toLocatorOrNull(), pageCount = 10))
    }

    @Test fun positionBeyondTheEndIsClamped() {
        val json = """{"href":"x","type":"application/pdf","locations":{"position":999}}"""
        assertEquals(9, pageIndexOf(json.toLocatorOrNull(), pageCount = 10))
    }
}
```

`reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfEngineDeviceTest.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.LocatorCodec
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.ReaderEngine
import dev.kent.windbook.reader.ReadiumServices
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfEngineDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val engine = ReaderEngine(ReadiumServices(context))

    @Test fun pdfOpensAsPdfSessionWithStableLocators() = runBlocking {
        val session = engine.open(PdfFixtures.boxed(context.cacheDir, "engine.pdf", pages = 5), BookFormat.PDF).getOrThrow()
        assertTrue(session is PdfSession)
        session as PdfSession
        assertEquals(5, session.pageCount)
        val json = session.locatorJsonFor(3)
        assertEquals(3, session.pageOf(json))
        assertEquals(0.6, LocatorCodec.progression(json)!!, 0.01) // Readium totalProgression = (4 - 1) / 5
        session.close()
    }

    @Test fun cropPlanFindsMirroredMargins() = runBlocking {
        val file = PdfFixtures.boxed(context.cacheDir, "mirror.pdf", pages = 12, mirrorShift = 40f)
        val session = engine.open(file, BookFormat.PDF).getOrThrow() as PdfSession
        val plan = session.pages.cropPlan()
        // Odd pages: box 140..340 of 400; even pages: 100..300.
        assertEquals(0.335f, plan.boxFor(0).left, 0.02f)
        assertEquals(0.235f, plan.boxFor(1).left, 0.02f)
        session.close()
    }

    @Test fun corruptPdfFailsToOpen() = runBlocking {
        val junk = context.cacheDir.resolve("corrupt.pdf").apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }
        assertTrue(engine.open(junk, BookFormat.PDF).isFailure)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :reader:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfLocatorsTest'`
Expected: compilation FAILS (`Unresolved reference: pageIndexOf`).

- [ ] **Step 3: `PdfSession` interface**

Append to `ReaderApi.kt`:

```kotlin
/** A PDF shown by WindBook's own renderer. Pages are 0-based everywhere in the app. */
interface PdfSession : ReaderSession {
    val pageCount: Int
    val pages: dev.kent.windbook.reader.pdf.PdfPageSource
    suspend fun firstPageSizes(): List<dev.kent.windbook.core.model.PageSize>
    fun locatorJsonFor(page: Int): String
    fun pageOf(locatorJson: String?): Int
}
```

- [ ] **Step 4: Page source**

`reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPages.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import android.util.LruCache
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.MarginDetector
import dev.kent.windbook.core.model.PageSize
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Bitmap cache bound: min(48 MB, maxMemory / 6). */
fun defaultCacheBytes(): Int = minOf(48L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 6).toInt()

/** [PdfPageSource] over a [PdfDocumentHandle]: caches page bitmaps and computes crop boxes once. */
class PdfPages(
    private val document: PdfDocumentHandle,
    maxCacheBytes: Int = defaultCacheBytes(),
) : PdfPageSource {

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val planLock = Mutex()
    private var plan: CropPlan? = null
    private val boxLock = Mutex()
    private val ownBoxes = HashMap<Int, CropBox?>()

    override val pageCount: Int get() = document.pageCount

    override fun pageSize(page: Int): PageSize = document.pageSize(page)

    override suspend fun measure(page: Int): PageSize = document.measure(page)

    override suspend fun cropPlan(): CropPlan = planLock.withLock {
        plan ?: run {
            val samples = MarginDetector.sampleIndices(pageCount)
            val boxes = samples.associateWith { ownBox(it) }
            CropPlan(
                odd = MarginDetector.bookBox(samples.filter { it % 2 == 0 }.map { boxes[it] }),
                even = MarginDetector.bookBox(samples.filter { it % 2 == 1 }.map { boxes[it] }),
            ).also { plan = it }
        }
    }

    override suspend fun pageCrop(page: Int, plan: CropPlan): CropBox =
        MarginDetector.forPage(plan.boxFor(page), ownBox(page))

    override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap {
        if (region != null) return document.render(page, crop, widthPx, region) // zoom tiles are not cached
        val key = "$page|$widthPx|$crop"
        cache.get(key)?.let { return it }
        return document.render(page, crop, widthPx).also { cache.put(key, it) }
    }

    private suspend fun ownBox(page: Int): CropBox? {
        boxLock.withLock { if (ownBoxes.containsKey(page)) return ownBoxes[page] }
        val luma = document.luminance(page)
        val box = MarginDetector.contentBox(luma.values, luma.width, luma.height)
        boxLock.withLock { ownBoxes[page] = box }
        return box
    }
}
```

- [ ] **Step 5: Session and locator mapping**

`reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderSession.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.reader.PdfSession
import dev.kent.windbook.reader.toJsonString
import dev.kent.windbook.reader.toLocatorOrNull
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication

/** 0-based page for a saved locator; Readium PDF positions are the 1-based page numbers. */
internal fun pageIndexOf(locator: Locator?, pageCount: Int): Int {
    val position = locator?.locations?.position ?: return 0
    return (position - 1).coerceIn(0, maxOf(0, pageCount - 1))
}

/** Readium keeps the publication (metadata, positions); [document] does the rendering. */
internal class PdfReaderSession(
    private val publication: Publication,
    private val document: PdfDocumentHandle,
    private val positions: List<Locator>,
) : PdfSession {
    override val format = BookFormat.PDF
    override val pageCount: Int get() = document.pageCount
    override val pages: PdfPageSource = PdfPages(document)

    override suspend fun firstPageSizes(): List<PageSize> = (0 until minOf(5, pageCount)).map { document.measure(it) }

    override fun locatorJsonFor(page: Int): String =
        positions[page.coerceIn(0, positions.lastIndex)].toJsonString()

    override fun pageOf(locatorJson: String?): Int = pageIndexOf(locatorJson?.toLocatorOrNull(), pageCount)

    override fun close() {
        document.close()
        publication.close()
    }
}
```

- [ ] **Step 6: Engine**

Replace the body of `ReaderEngine.open` in `ReaderEngine.kt`:

```kotlin
    override suspend fun open(file: File, format: BookFormat): Result<ReaderSession> =
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext Result.failure(ReaderException("Missing file: $file"))
            services.openPublication(file).mapCatching { publication ->
                val positions = publication.positions()
                when (format) {
                    BookFormat.EPUB -> ReadiumReaderSession(format, publication, positionCount = positions.size)
                    BookFormat.PDF -> try {
                        // Locators come from Readium positions; a PDF without them can't be resumed.
                        if (positions.isEmpty()) throw ReaderException("PDF has no positions")
                        PdfReaderSession(publication, PdfDocumentHandle.open(file), positions)
                    } catch (e: Exception) {
                        publication.close()
                        throw ReaderException("PDF can't be rendered: ${e.message}")
                    }
                }
            }
        }
```

Add `import dev.kent.windbook.reader.pdf.PdfDocumentHandle` and `import dev.kent.windbook.reader.pdf.PdfReaderSession`.

- [ ] **Step 7: Host fragment becomes EPUB-only**

In `ReaderHostFragment.kt`:
- In `onCreate`, replace the `when (format)` with:

```kotlin
        require(format == BookFormat.EPUB) { "ReaderHostFragment hosts EPUB only; PDFs use PdfReaderView" }
        childFragmentManager.fragmentFactory = EpubNavigatorFragment.createDummyFactory()
```

- In `bind`, replace the two `when (readium.format)` blocks with:

```kotlin
        childFragmentManager.fragmentFactory = EpubNavigatorFactory(readium.publication)
            .createFragmentFactory(
                initialLocator = initialLocator,
                initialPreferences = initialEpubPreferences(darkTheme),
            )
        childFragmentManager.commitNow {
            childFragmentManager.findFragmentByTag(NAVIGATOR_TAG)?.let { remove(it) }
            add(R.id.windbook_navigator_container, EpubNavigatorFragment::class.java, Bundle(), NAVIGATOR_TAG)
        }
```

- Delete the now-unused imports `PdfiumEngineProvider`, `PdfNavigatorFactory` and `PdfNavigatorFragment`.

- [ ] **Step 8: Run unit and device tests**

Run: `./gradlew :reader:testDebugUnitTest`
Expected: PASS (12 existing + 3 `PdfLocatorsTest`).

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: PASS (3 existing EPUB host tests + 6 `PdfDocumentHandleTest` + 3 `PdfEngineDeviceTest`).

- [ ] **Step 9: Commit**

```bash
git add reader/src
git commit -m "feat(reader): PDF session with own renderer, crop plan and M1-compatible locators; host fragment is EPUB-only"
```

---

### Task 5: ReaderViewModel PDF state and DI

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/di/AppModule.kt`
- Modify: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelTest.kt` (constructor gets the new argument)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelPdfTest.kt`

**Interfaces:**
- Consumes: `PdfSession` (Task 4), `DisplayPreferencesRepository` (Task 2), `BookDisplayPrefs.resolve`, `EffectiveDisplay`, `PdfLayout` (Task 1), `readingProgression` (existing).
- Produces:
  - `ReaderViewModel(bookId, library, opener, displayPrefs: DisplayPreferencesRepository)`
  - `val pdfDisplay: StateFlow<EffectiveDisplay?>` (null for EPUB or before load)
  - `fun setPdfLayout(layout: PdfLayout)`, `fun setCropMargins(enabled: Boolean)`
  - `fun onPdfPageChanged(page: Int)`, `fun initialPdfPage(): Int`

- [ ] **Step 1: Write the failing test**

`app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelPdfTest.kt`:

```kotlin
package dev.kent.windbook.reader

import android.graphics.Bitmap
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import dev.kent.windbook.core.data.DisplayPreferencesRepository
import dev.kent.windbook.core.data.LibraryRepository
import dev.kent.windbook.core.data.db.BookEntity
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

    private class FakePdf(private val size: PageSize, override val pageCount: Int = 10) : PdfSession {
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

    private fun vm(size: PageSize) = ReaderViewModel("a", library, BookOpener { _, _ -> Result.success(FakePdf(size)) }, prefs)

    @Test fun portraitPdfDefaultsToVerticalWithCrop() = runTest {
        val display = vm(PageSize(400f, 600f)).pdfDisplay.filterNotNull().first()
        assertEquals(EffectiveDisplay(PdfLayout.VERTICAL, true), display)
    }

    @Test fun slidesDefaultToPagedWithoutCrop() = runTest {
        val display = vm(PageSize(720f, 540f)).pdfDisplay.filterNotNull().first()
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), display)
    }

    @Test fun choosingPagedIsSavedForTheBook() = runTest {
        val model = vm(PageSize(400f, 600f))
        model.pdfDisplay.test {
            while (awaitItem() == null) Unit
            model.setPdfLayout(PdfLayout.PAGED)
            var state = awaitItem()
            while (state?.layout != PdfLayout.PAGED) state = awaitItem()
            assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), state)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, null), prefs.observe("a").first())
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

    /** Saving hops to Room's IO dispatcher; poll in real time (runTest's virtual clock would skip the waits). */
    private suspend fun LibraryRepository.savedLocatorFlowFirst(id: String, matches: (String?) -> Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            repeat(200) {
                if (matches(savedLocator(id))) return@withContext
                kotlinx.coroutines.delay(10)
            }
            throw AssertionError("locator never saved: ${savedLocator(id)}")
        }
}
```

In `ReaderViewModelTest.kt`:
- Add the field `private lateinit var prefs: DisplayPreferencesRepository`.
- In `setUp`, add `prefs = DisplayPreferencesRepository(db.bookPreferencesDao())` after `library = …`.
- Replace every `ReaderViewModel("…", library, …)` with the same call plus `, prefs` as the last argument. For example, `ReaderViewModel("a", library, opener)` becomes `ReaderViewModel("a", library, opener, prefs)`.
- Add the import `dev.kent.windbook.core.data.DisplayPreferencesRepository`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderViewModel*'`
Expected: compilation FAILS (too many arguments for `ReaderViewModel`, `Unresolved reference: pdfDisplay`).

- [ ] **Step 3: ViewModel**

In `ReaderViewModel.kt`:
- Change the constructor to:

```kotlin
class ReaderViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val library: LibraryRepository,
    private val opener: BookOpener,
    private val displayPrefs: DisplayPreferencesRepository,
) : ViewModel() {
```

- Add fields after `_overlayVisible`:

```kotlin
    private val _pdfDisplay = MutableStateFlow<EffectiveDisplay?>(null)

    /** How a PDF is shown (null for EPUB or while loading). */
    val pdfDisplay: StateFlow<EffectiveDisplay?> = _pdfDisplay.asStateFlow()

    private var currentPrefs = BookDisplayPrefs()
```

- In `open()`, inside `.onSuccess { opened -> … }` **before** `_state.value = ReaderUiState.Ready(...)`, add:

```kotlin
                if (opened is PdfSession) {
                    val firstPages = opened.firstPageSizes()
                    viewModelScope.launch {
                        displayPrefs.observe(bookId).collect { prefs ->
                            currentPrefs = prefs
                            _pdfDisplay.value = prefs.resolve(firstPages)
                        }
                    }
                }
```

- Add the methods after `toggleOverlay()`:

```kotlin
    fun setPdfLayout(layout: PdfLayout) = updatePrefs { it.copy(pdfLayout = layout) }

    fun setCropMargins(enabled: Boolean) = updatePrefs { it.copy(cropMargins = enabled) }

    private fun updatePrefs(change: (BookDisplayPrefs) -> BookDisplayPrefs) {
        val next = change(currentPrefs)
        viewModelScope.launch { displayPrefs.set(bookId, next) }
    }

    /** PDF pages are 0-based; progress uses page / pageCount so the last page reaches 100 %. */
    fun onPdfPageChanged(page: Int) {
        val pdf = session as? PdfSession ?: return
        onLocatorChanged(
            locatorJson = pdf.locatorJsonFor(page),
            progression = readingProgression(position = page + 1, positionCount = pdf.pageCount, totalProgression = null),
            chapterTitle = null,
        )
    }

    /** Where a (re-)created PDF view starts: the newest position, else the saved one. */
    fun initialPdfPage(): Int = (session as? PdfSession)?.pageOf(latestLocatorJson) ?: 0
```

- Add the imports `dev.kent.windbook.core.data.DisplayPreferencesRepository`, `dev.kent.windbook.core.model.BookDisplayPrefs`, `dev.kent.windbook.core.model.EffectiveDisplay`, `dev.kent.windbook.core.model.PdfLayout` and `dev.kent.windbook.core.model.resolve`.

- [ ] **Step 4: DI**

In `AppModule.kt`, add the import `dev.kent.windbook.core.data.DisplayPreferencesRepository` and this provider after `libraryRepository`:

```kotlin
    @Provides @Singleton
    fun displayPreferences(db: WindBookDatabase): DisplayPreferencesRepository =
        DisplayPreferencesRepository(db.bookPreferencesDao())
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.*'`
Expected: `ReaderViewModelTest` (existing) and `ReaderViewModelPdfTest` (5) PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt app/src/main/kotlin/dev/kent/windbook/di/AppModule.kt app/src/test/kotlin/dev/kent/windbook/reader
git commit -m "feat(app): reader view model keeps per-book PDF display and reports PDF pages"
```

---

### Task 6: Reader overlay View menu (PDF only)

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` (`ReaderOverlay` only)
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt` (+ `ViewMode`)
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`

**Interfaces:**
- Consumes: `EffectiveDisplay`, `PdfLayout` (Task 1).
- Produces: `ReaderOverlay(…existing…, pdfDisplay: EffectiveDisplay? = null, onPdfLayoutChange: (PdfLayout) -> Unit = {}, onCropMarginsChange: (Boolean) -> Unit = {})`

- [ ] **Step 1: Strings**

`values/strings.xml`:

```xml
    <string name="cd_view_mode">View</string>
    <string name="layout_vertical">Vertical scroll</string>
    <string name="layout_paged">Page by page</string>
    <string name="crop_margins">Crop margins</string>
    <string name="page_render_failed">This page can\'t be shown</string>
```

`values-vi/strings.xml`:

```xml
    <string name="cd_view_mode">Chế độ xem</string>
    <string name="layout_vertical">Cuộn dọc</string>
    <string name="layout_paged">Lật từng trang</string>
    <string name="crop_margins">Cắt lề trắng</string>
    <string name="page_render_failed">Không hiển thị được trang này</string>
```

- [ ] **Step 2: Write the failing tests**

Append to `ReaderOverlayTest` (inside the class), and add the imports `androidx.compose.ui.test.assertIsOff`, `androidx.compose.ui.test.assertIsOn`, `dev.kent.windbook.core.model.EffectiveDisplay`, `dev.kent.windbook.core.model.PdfLayout`:

```kotlin
    @Test fun epubHasNoViewButton() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Dune", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
            )
        }
        compose.onNodeWithContentDescription("View").assertDoesNotExist()
    }

    @Test fun pdfViewMenuSwitchesLayout() {
        val layouts = mutableListOf<PdfLayout>()
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
                onPdfLayoutChange = { layouts += it },
            )
        }
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithText("Page by page").performClick()
        assertEquals(listOf(PdfLayout.PAGED), layouts)
    }

    @Test fun cropItemShowsAndTogglesState() {
        val crops = mutableListOf<Boolean>()
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
                onCropMarginsChange = { crops += it },
            )
        }
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithText("Crop margins").assertIsOn().performClick()
        assertEquals(listOf(false), crops)
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderOverlayTest'`
Expected: compilation FAILS (`No parameter with name 'pdfDisplay'`).

- [ ] **Step 4: Icon**

Add to `WindBookIcons` after `Settings`:

```kotlin
    val ViewMode = icon(
        "ViewMode",
        "M20,13H3c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h17c0.55,0 1,-0.45 1,-1v-6c0,-0.55 -0.45,-1 -1,-1zM20,3H3c-0.55,0 -1,0.45 -1,1v6c0,0.55 0.45,1 1,1h17c0.55,0 1,-0.45 1,-1V4c0,-0.55 -0.45,-1 -1,-1z",
    )
```

(Material `view_agenda`, Apache-2.0.)

- [ ] **Step 5: Overlay**

In `ReaderScreen.kt`, change the `ReaderOverlay` signature to add the three parameters at the end:

```kotlin
    onBack: () -> Unit,
    pdfDisplay: EffectiveDisplay? = null,
    onPdfLayoutChange: (PdfLayout) -> Unit = {},
    onCropMarginsChange: (Boolean) -> Unit = {},
) {
```

Replace the balancing spacer in the top bar:

```kotlin
                // Balances the back button so the title is truly centred.
                Spacer(Modifier.width(48.dp))
```

with:

```kotlin
                if (pdfDisplay != null) {
                    ViewMenu(pdfDisplay, onPdfLayoutChange, onCropMarginsChange)
                } else {
                    // Balances the back button so the title is truly centred.
                    Spacer(Modifier.width(48.dp))
                }
```

Add this composable below `ReaderOverlay`:

```kotlin
@Composable
private fun ViewMenu(
    display: EffectiveDisplay,
    onLayoutChange: (PdfLayout) -> Unit,
    onCropChange: (Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(WindBookIcons.ViewMode, contentDescription = stringResource(R.string.cd_view_mode))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(PdfLayout.VERTICAL to R.string.layout_vertical, PdfLayout.PAGED to R.string.layout_paged).forEach { (layout, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = display.layout == layout, onClick = null) },
                    onClick = {
                        open = false
                        if (display.layout != layout) onLayoutChange(layout)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.crop_margins)) },
                leadingIcon = { Checkbox(checked = display.cropMargins, onCheckedChange = null) },
                onClick = {
                    open = false
                    onCropChange(!display.cropMargins)
                },
                // TalkBack hears "Crop margins, checkbox, checked".
                modifier = Modifier.semantics {
                    role = Role.Checkbox
                    toggleableState = ToggleableState(display.cropMargins)
                },
            )
        }
    }
}
```

Add the imports `androidx.compose.ui.semantics.role`, `androidx.compose.ui.semantics.semantics`, `androidx.compose.ui.semantics.toggleableState`, `androidx.compose.ui.state.ToggleableState`, `androidx.compose.material3.Checkbox`, `androidx.compose.material3.DropdownMenu`, `androidx.compose.material3.DropdownMenuItem`, `androidx.compose.material3.HorizontalDivider`, `androidx.compose.material3.RadioButton`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.setValue`, `androidx.compose.ui.semantics.Role`, `dev.kent.windbook.core.model.EffectiveDisplay` and `dev.kent.windbook.core.model.PdfLayout`. (`getValue` and `remember` are already imported.)

- [ ] **Step 6: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderOverlayTest'`
Expected: all overlay tests PASS (existing 5 + 3 new).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt app/src/main/kotlin/dev/kent/windbook/ui/icons/WindBookIcons.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-vi/strings.xml app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt
git commit -m "feat(app): PDF view menu in the reader bar (vertical / page by page, crop margins)"
```

---

### Task 7: PdfReaderView (vertical scroll + page by page)

**Files:**
- Create: `app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfReaderViewTest.kt`

**Interfaces:**
- Consumes: `PdfPageSource`, `PixelRegion`, `renderedHeight` (Task 3). `CropBox`, `CropPlan`, `PdfLayout`, `PageSize` (Task 1). `LocalKeyEventDispatcher`, `KeyEventDispatcher.PageTurner` (existing). `R.string.page_render_failed` (Task 6).
- Produces: `@Composable fun PdfReaderView(source: PdfPageSource, layout: PdfLayout, cropMargins: Boolean, initialPage: Int, onPageChanged: (Int) -> Unit, onCenterTap: () -> Unit, modifier: Modifier = Modifier)`, plus `const val PDF_VERTICAL_TAG = "pdf-vertical"` and `const val PDF_PAGED_TAG = "pdf-paged"`

- [ ] **Step 1: Write the failing tests**

`app/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfReaderViewTest.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.click
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.reader.pdf.PDF_PAGED_TAG
import dev.kent.windbook.reader.pdf.PDF_VERTICAL_TAG
import dev.kent.windbook.reader.pdf.PdfReaderView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp")
class PdfReaderViewTest {
    @get:Rule val compose = createComposeRule()

    private class FakeSource(private val sizes: List<PageSize>) : PdfPageSource {
        val rendered = mutableListOf<Int>()
        override val pageCount = sizes.size
        override fun pageSize(page: Int) = sizes[page]
        override suspend fun measure(page: Int) = sizes[page]
        override suspend fun cropPlan() = CropPlan.NONE
        override suspend fun pageCrop(page: Int, plan: CropPlan) = CropBox.FULL
        override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap {
            rendered += page
            return Bitmap.createBitmap(region?.width ?: widthPx, region?.height ?: renderedHeight(sizes[page], crop, widthPx), Bitmap.Config.ARGB_8888)
        }
    }

    private val book = FakeSource(List(20) { PageSize(400f, 600f) })
    private val dispatcher = KeyEventDispatcher()
    private val pages = mutableListOf<Int>()
    private var taps = 0

    private fun show(source: PdfPageSource = book, layout: PdfLayout, initialPage: Int = 0) {
        compose.setContent {
            CompositionLocalProvider(LocalKeyEventDispatcher provides dispatcher) {
                PdfReaderView(
                    source = source, layout = layout, cropMargins = false, initialPage = initialPage,
                    onPageChanged = { pages += it }, onCenterTap = { taps++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun pressVolumeDown() {
        compose.runOnIdle {
            dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN))
            dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN))
        }
        compose.waitForIdle()
    }

    @Test fun verticalStartsAtTheSavedPage() {
        show(layout = PdfLayout.VERTICAL, initialPage = 7)
        assertEquals(7, pages.last())
        assertTrue(7 in book.rendered)
    }

    @Test fun verticalScrollReportsLaterPages() {
        show(layout = PdfLayout.VERTICAL)
        repeat(3) { compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        assertTrue("pages reported: $pages", pages.last() > 0)
    }

    @Test fun volumeKeyScrollsVertically() {
        show(layout = PdfLayout.VERTICAL)
        repeat(3) { pressVolumeDown() }
        assertTrue("pages reported: $pages", pages.last() >= 1)
    }

    @Test fun singleTapTogglesTheOverlay() {
        show(layout = PdfLayout.VERTICAL)
        compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test fun pagedSwipeTurnsThePage() {
        show(layout = PdfLayout.PAGED, initialPage = 2)
        compose.onNodeWithTag(PDF_PAGED_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(3, pages.last())
    }

    @Test fun pagedVolumeKeyTurnsThePage() {
        show(layout = PdfLayout.PAGED)
        pressVolumeDown()
        assertEquals(1, pages.last())
    }

    @Test fun pagedRightEdgeTapTurnsAndCentreTapToggles() {
        show(layout = PdfLayout.PAGED)
        compose.onNodeWithTag(PDF_PAGED_TAG).performTouchInput { click(centerRight.copy(x = width * 0.9f)) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, pages.last())
        compose.onNodeWithTag(PDF_PAGED_TAG).performTouchInput { click(center) }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(1, taps)
    }

    @Test fun switchingLayoutKeepsThePage() {
        var layout by mutableStateOf(PdfLayout.VERTICAL)
        compose.setContent {
            CompositionLocalProvider(LocalKeyEventDispatcher provides dispatcher) {
                PdfReaderView(book, layout, cropMargins = false, initialPage = 5, onPageChanged = { pages += it }, onCenterTap = {})
            }
        }
        compose.waitForIdle()
        layout = PdfLayout.PAGED
        compose.waitForIdle()
        assertEquals(5, pages.last())
    }

    @Test fun mixedPageSizesLayOutIndependently() {
        val mixed = FakeSource(List(6) { if (it == 2) PageSize(800f, 400f) else PageSize(400f, 600f) })
        show(source = mixed, layout = PdfLayout.VERTICAL)
        repeat(2) { compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        assertTrue("landscape page rendered: ${mixed.rendered}", 2 in mixed.rendered)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfReaderViewTest'`
Expected: compilation FAILS (`Unresolved reference: PdfReaderView`).

- [ ] **Step 3: Implement the view**

`app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.kent.windbook.R
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

const val PDF_VERTICAL_TAG = "pdf-vertical"
const val PDF_PAGED_TAG = "pdf-paged"

private const val MAX_ZOOM = 4f
private const val DOUBLE_TAP_ZOOM = 2.5f
private val PagedSurround = Color(0xFF141210)

/**
 * WindBook's PDF view (spec §3.1). [initialPage] is read once; afterwards the view owns the current page
 * and keeps it when [layout] changes.
 */
@Composable
fun PdfReaderView(
    source: PdfPageSource,
    layout: PdfLayout,
    cropMargins: Boolean,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by rememberSaveable(source) { mutableIntStateOf(initialPage.coerceIn(0, source.pageCount - 1)) }
    var plan by remember(source, cropMargins) { mutableStateOf<CropPlan?>(null) }
    val pageCrops = remember(source, cropMargins) { mutableStateMapOf<Int, CropBox>() }
    LaunchedEffect(source, cropMargins) {
        if (cropMargins) plan = source.cropPlan()
    }
    val cropFor: (Int) -> CropBox = { page ->
        if (!cropMargins) CropBox.FULL else pageCrops[page] ?: plan?.boxFor(page) ?: CropBox.FULL
    }
    val resolveCrop: suspend (Int) -> Unit = { page ->
        val p = plan
        if (cropMargins && p != null && page !in pageCrops) pageCrops[page] = source.pageCrop(page, p)
    }
    val report: (Int) -> Unit = { page ->
        currentPage = page
        onPageChanged(page)
    }
    when (layout) {
        PdfLayout.VERTICAL -> VerticalPages(source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier)
        PdfLayout.PAGED -> PagedPages(source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier)
    }
}

@Composable
private fun VerticalPages(
    source: PdfPageSource,
    startPage: Int,
    cropFor: (Int) -> CropBox,
    resolveCrop: suspend (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect(onPageChanged)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val pageWidthPx = (viewportW * zoom).roundToInt()

        PageTurnKeys { forward ->
            scope.launch { listState.animateScrollBy(viewportH * 0.9f * if (forward) 1 else -1) }
            true
        }

        // Prefetch ±2 pages into the cache so scrolling lands on ready bitmaps (spec §3.3).
        LaunchedEffect(listState, source, viewportW) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { first ->
                for (p in (first - 2)..(first + 3)) {
                    if (p !in 0 until source.pageCount) continue
                    try {
                        source.render(p, cropFor(p), viewportW)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // A broken page shows its own message when it scrolls in.
                    }
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .testTag(PDF_VERTICAL_TAG)
                .pinchZoom { change, centroid ->
                    val next = (zoom * change).coerceIn(1f, MAX_ZOOM)
                    val ratio = next / zoom
                    if (ratio != 1f) {
                        zoom = next
                        scope.launch {
                            hScroll.scrollTo(((hScroll.value + centroid.x) * ratio - centroid.x).roundToInt().coerceAtLeast(0))
                            listState.scrollBy((listState.firstVisibleItemScrollOffset + centroid.y) * (ratio - 1f))
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCenterTap() },
                        onDoubleTap = { tap ->
                            val target = if (zoom > 1.01f) 1f else DOUBLE_TAP_ZOOM
                            val ratio = target / zoom
                            zoom = target
                            scope.launch {
                                hScroll.scrollTo(((hScroll.value + tap.x) * ratio - tap.x).roundToInt().coerceAtLeast(0))
                                listState.scrollBy((listState.firstVisibleItemScrollOffset + tap.y) * (ratio - 1f))
                            }
                        },
                    )
                }
                .horizontalScroll(hScroll, enabled = zoom > 1f),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.width(with(density) { pageWidthPx.toDp() }).height(with(density) { viewportH.toDp() }),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = source.pageCount, key = { it }) { page ->
                    LaunchedEffect(page) {
                        source.measure(page)
                        resolveCrop(page)
                    }
                    val crop = cropFor(page)
                    val heightPx = renderedHeight(source.pageSize(page), crop, pageWidthPx)
                    PageBitmap(
                        source = source,
                        page = page,
                        crop = crop,
                        renderWidthPx = viewportW,
                        modifier = Modifier
                            .width(with(density) { pageWidthPx.toDp() })
                            .height(with(density) { heightPx.toDp() }),
                    )
                }
            }
        }
    }
}

@Composable
private fun PagedPages(
    source: PdfPageSource,
    startPage: Int,
    cropFor: (Int) -> CropBox,
    resolveCrop: suspend (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    modifier: Modifier,
) {
    val pager = rememberPagerState(initialPage = startPage) { source.pageCount }
    val scope = rememberCoroutineScope()
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            zoom = 1f
            pan = Offset.Zero
            onPageChanged(page)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PagedSurround)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight

        PageTurnKeys { forward ->
            val target = (pager.currentPage + if (forward) 1 else -1).coerceIn(0, source.pageCount - 1)
            scope.launch { pager.animateScrollToPage(target) }
            true
        }

        HorizontalPager(
            state = pager,
            userScrollEnabled = zoom == 1f,
            beyondViewportPageCount = 1,
            key = { it },
            modifier = Modifier.fillMaxSize().testTag(PDF_PAGED_TAG),
        ) { page ->
            LaunchedEffect(page) {
                source.measure(page)
                resolveCrop(page)
            }
            val crop = cropFor(page)
            val size = source.pageSize(page)
            val pageAspect = (crop.width * size.width) / (crop.height * size.height)
            val fitW = if (viewportW / pageAspect <= viewportH) viewportW else (viewportH * pageAspect).roundToInt()
            val fitH = (fitW / pageAspect).roundToInt()
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(page) {
                        detectTapGestures(
                            onTap = { tap ->
                                when {
                                    tap.x < viewportW * 0.25f -> scope.launch { pager.animateScrollToPage((page - 1).coerceAtLeast(0)) }
                                    tap.x > viewportW * 0.75f -> scope.launch { pager.animateScrollToPage((page + 1).coerceAtMost(source.pageCount - 1)) }
                                    else -> onCenterTap()
                                }
                            },
                            onDoubleTap = { tap ->
                                if (zoom > 1f) {
                                    zoom = 1f
                                    pan = Offset.Zero
                                } else {
                                    zoom = DOUBLE_TAP_ZOOM
                                    pan = (Offset(viewportW / 2f, viewportH / 2f) - tap) * (DOUBLE_TAP_ZOOM - 1f)
                                }
                            },
                        )
                    }
                    .then(if (zoom > 1f) Modifier.pointerInput(Unit) { detectDragGestures { _, drag -> pan += drag } } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                PageBitmap(
                    source = source,
                    page = page,
                    crop = crop,
                    renderWidthPx = if (zoom > 1f && pager.currentPage == page) fitW * 2 else fitW,
                    modifier = Modifier
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            if (pager.currentPage == page) {
                                scaleX = zoom
                                scaleY = zoom
                                translationX = pan.x
                                translationY = pan.y
                            }
                        },
                )
            }
        }
    }
}

/** One page bitmap; a failed page shows a message and leaves its neighbours alone. */
@Composable
private fun PageBitmap(source: PdfPageSource, page: Int, crop: CropBox, renderWidthPx: Int, modifier: Modifier) {
    var image by remember(page, crop, renderWidthPx) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(page, crop, renderWidthPx) { mutableStateOf(false) }
    LaunchedEffect(page, crop, renderWidthPx) {
        try {
            image = source.render(page, crop, renderWidthPx).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
    }
    Box(modifier.background(Color.White)) {
        image?.let { Image(it, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize()) }
        if (failed) {
            Text(
                stringResource(R.string.page_render_failed),
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Registers hardware page-turn keys (volume, page up/down) while this view is shown. */
@Composable
private fun PageTurnKeys(turn: (forward: Boolean) -> Boolean) {
    val dispatcher = LocalKeyEventDispatcher.current
    DisposableEffect(dispatcher, turn) {
        val turner = KeyEventDispatcher.PageTurner { forward -> turn(forward) }
        dispatcher.register(turner)
        onDispose { dispatcher.unregister(turner) }
    }
}

/** Two-finger pinch only; one-finger drags fall through to scrolling. */
private fun Modifier.pinchZoom(onZoom: (change: Float, centroid: Offset) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val change = event.calculateZoom()
                if (change != 1f) onZoom(change, event.calculateCentroid(useCurrent = true))
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}
```

`androidx.compose.foundation.layout.offset`, `IntOffset` and `delay` are not used by this version. Remove them if the compiler warns, and keep the imports minimal. Zoomed-detail tiles are delivered in Step 6.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfReaderViewTest'`
Expected: 9 tests PASS. If `pagedRightEdgeTapTurnsAndCentreTapToggles` sees the overlay tap counted before the page turn, check the edge tap uses `x > 0.75 × width`. The test taps at 0.9 × width.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader/pdf app/src/test/kotlin/dev/kent/windbook/reader/pdf
git commit -m "feat(app): PdfReaderView with vertical scroll, page-by-page mode, pinch and double-tap zoom"
```

- [ ] **Step 6: Sharp zoomed detail (vertical mode)**

When zoomed, the base bitmap (viewport width) is scaled up and looks soft. Add a detail tile per visible page once scrolling and zooming settle (spec §3.3).

In `VerticalPages`, after `var zoom …`, add:

```kotlin
    val details = remember { mutableStateMapOf<Int, Pair<PixelRegion, ImageBitmap>>() }
    var pinching by remember { mutableStateOf(false) }
```

Set `pinching = true` inside the `pinchZoom` callback, and `pinching = false` in a `LaunchedEffect(zoom) { delay(150); pinching = false }`. Then add this effect inside `BoxWithConstraints`, after `pageWidthPx` is computed:

```kotlin
        LaunchedEffect(zoom, pageWidthPx) {
            details.clear()
            if (zoom <= 1.05f) return@LaunchedEffect
            snapshotFlow {
                Triple(listState.isScrollInProgress || hScroll.isScrollInProgress || pinching, listState.layoutInfo.visibleItemsInfo.map { it.index to it.offset }, hScroll.value)
            }.distinctUntilChanged().collect { (moving, visible, scrollX) ->
                if (moving) return@collect
                delay(150)
                for ((page, offsetY) in visible) {
                    val crop = cropFor(page)
                    val pageH = renderedHeight(source.pageSize(page), crop, pageWidthPx)
                    val top = (-offsetY).coerceAtLeast(0)
                    val bottom = (viewportH - offsetY).coerceAtMost(pageH)
                    if (bottom <= top) continue
                    val region = PixelRegion(scrollX, top, viewportW.coerceAtMost(pageWidthPx - scrollX), bottom - top)
                    if (details[page]?.first == region) continue
                    runCatching { source.render(page, crop, pageWidthPx, region) }
                        .onSuccess { details[page] = region to it.asImageBitmap() }
                }
            }
        }
```

In the `items { page -> … }` block, wrap `PageBitmap` in a `Box` with the same width/height modifier, and draw the tile on top:

```kotlin
                    Box(Modifier.width(with(density) { pageWidthPx.toDp() }).height(with(density) { heightPx.toDp() })) {
                        PageBitmap(source, page, crop, renderWidthPx = viewportW, modifier = Modifier.fillMaxSize())
                        details[page]?.let { (region, tile) ->
                            Image(
                                tile,
                                contentDescription = null,
                                modifier = Modifier
                                    .offset { IntOffset(region.x, region.y) }
                                    .size(with(density) { region.width.toDp() }, with(density) { region.height.toDp() }),
                            )
                        }
                    }
```

Keep the `offset`, `IntOffset` and `delay` imports this step needs.

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfReaderViewTest'`
Expected: still 9 PASS. Sharpness is verified on the emulator in Task 9.

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt
git commit -m "feat(app): sharp zoomed PDF detail tiles for visible pages"
```

---

### Task 8: Reader wiring, EPUB edge spacing, landscape covers

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` (`ReaderRoute`, `ReaderSurface`)
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReaderTheme.kt` (+ `epubPageBackground`)
- Modify: `app/src/main/kotlin/dev/kent/windbook/ui/components/BookCover.kt`
- Test: `reader/src/test/kotlin/dev/kent/windbook/reader/ReaderThemeTest.kt`, `app/src/test/kotlin/dev/kent/windbook/ui/components/BookCoverTest.kt`

**Interfaces:**
- Consumes: `PdfSession` (Task 4), `ReaderViewModel.pdfDisplay/setPdfLayout/setCropMargins/onPdfPageChanged/initialPdfPage` (Task 5), `ReaderOverlay(pdfDisplay, onPdfLayoutChange, onCropMarginsChange)` (Task 6), `PdfReaderView` (Task 7).
- Produces: `fun epubPageBackground(darkTheme: Boolean): Int` in `:reader`. `internal fun coverScaleFor(width: Float, height: Float): ContentScale` in `BookCover.kt`.

- [ ] **Step 1: Write the failing tests**

Append to `ReaderThemeTest` (inside the class):

```kotlin
    @Test fun epubPageBackgroundMatchesTheTheme() {
        assertEquals(Theme.SEPIA.backgroundColor, epubPageBackground(darkTheme = false))
        assertEquals(Theme.DARK.backgroundColor, epubPageBackground(darkTheme = true))
    }
```

Append to `BookCoverTest` (inside the class), and add the import `androidx.compose.ui.layout.ContentScale`:

```kotlin
    @Test fun landscapeCoversFitWholeAndPortraitCoversFill() {
        assertEquals(ContentScale.Fit, coverScaleFor(720f, 540f))
        assertEquals(ContentScale.Crop, coverScaleFor(400f, 600f))
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :reader:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderThemeTest' :app:testDebugUnitTest --tests 'dev.kent.windbook.ui.components.BookCoverTest'`
Expected: compilation FAILS (`Unresolved reference: epubPageBackground`, `coverScaleFor`).

- [ ] **Step 3: Theme helper**

Append to `ReaderTheme.kt`:

```kotlin
/** Colour of the EPUB page, used to paint the spacing around it so the edges blend in. */
fun epubPageBackground(darkTheme: Boolean): Int = (if (darkTheme) Theme.DARK else Theme.SEPIA).backgroundColor
```

- [ ] **Step 4: Landscape covers**

In `BookCover.kt`, add:

```kotlin
/** Wide covers (slides, landscape PDFs) are shown whole; book-shaped covers fill the 2:3 frame. */
internal fun coverScaleFor(width: Float, height: Float): ContentScale =
    if (width > height) ContentScale.Fit else ContentScale.Crop
```

Replace the `AsyncImage` line with:

```kotlin
        var scale by remember(coverPath) { mutableStateOf(ContentScale.Crop) }
        AsyncImage(
            model = File(coverPath),
            contentDescription = null,
            contentScale = scale,
            onSuccess = { state -> state.painter.intrinsicSize.let { scale = coverScaleFor(it.width, it.height) } },
            modifier = shaped.background(generatedCoverTint(bookId)),
        )
```

Add the imports `androidx.compose.runtime.getValue`, `androidx.compose.runtime.mutableStateOf`, `androidx.compose.runtime.remember` and `androidx.compose.runtime.setValue`.

- [ ] **Step 5: Wire PDF and EPUB in `ReaderRoute`**

In `ReaderScreen.kt`, replace the `is ReaderUiState.Ready -> Box(Modifier.fillMaxSize()) { … }` branch with:

```kotlin
        is ReaderUiState.Ready -> Box(Modifier.fillMaxSize()) {
            val pdf = current.session as? PdfSession
            val pdfDisplay by viewModel.pdfDisplay.collectAsStateWithLifecycle()
            if (pdf != null) {
                pdfDisplay?.let { display ->
                    val initialPage = remember(pdf) { viewModel.initialPdfPage() }
                    PdfReaderView(
                        source = pdf.pages,
                        layout = display.layout,
                        cropMargins = display.cropMargins,
                        initialPage = initialPage,
                        onPageChanged = viewModel::onPdfPageChanged,
                        onCenterTap = viewModel::toggleOverlay,
                    )
                }
            } else {
                ReaderSurface(current, viewModel)
            }
            ReaderOverlay(
                visible = overlay,
                title = current.title,
                chapterTitle = chapterTitle,
                progression = progression,
                canMarkFinished = canMarkFinished,
                onMarkFinished = viewModel::markFinished,
                onBack = onBack,
                pdfDisplay = if (pdf != null) pdfDisplay else null,
                onPdfLayoutChange = viewModel::setPdfLayout,
                onCropMarginsChange = viewModel::setCropMargins,
            )
        }
```

- [ ] **Step 6: EPUB edge spacing**

In `ReaderSurface`, replace:

```kotlin
    AndroidFragment<ReaderHostFragment>(
        modifier = Modifier.fillMaxSize(),
```

with:

```kotlin
    AndroidFragment<ReaderHostFragment>(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(epubPageBackground(darkTheme)))
            // Text never touches the screen edge or sits under a punch-hole camera (spec §3.6).
            .windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Vertical).union(WindowInsets(top = 16.dp, bottom = 16.dp)),
            ),
```

Add the imports `androidx.compose.foundation.layout.WindowInsets`, `androidx.compose.foundation.layout.WindowInsetsSides`, `androidx.compose.foundation.layout.displayCutout`, `androidx.compose.foundation.layout.only`, `androidx.compose.foundation.layout.union`, `androidx.compose.foundation.layout.windowInsetsPadding`, `androidx.compose.ui.graphics.Color`, `dev.kent.windbook.reader.pdf.PdfReaderView` (app package) and `dev.kent.windbook.reader.epubPageBackground`. `PdfSession` lives in the same package name (`dev.kent.windbook.reader`) in the `:reader` module, so it needs no import.

- [ ] **Step 7: Run all JVM tests, lint and build**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL. No new lint warnings (the new strings are all used).

- [ ] **Step 8: Commit**

```bash
git add app/src/main reader/src/main app/src/test reader/src/test
git commit -m "feat: PDFs open in WindBook's own view; EPUB keeps clear of screen edges; landscape covers fit"
```

---

### Task 9: Device verification, measurements and docs

**Files:**
- Modify: `docs/testing/m1-manual-checklist.md` (new "PDF display" section)
- Modify: `docs/superpowers/plans/2026-10-13-windbook-roadmap.md` (new row)
- Modify: `docs/research/2026-10-15-library-analysis.md` ("After" column)

**Interfaces:**
- Consumes: the whole feature.
- Produces: measured numbers for spec criteria 1–5, plus screenshots in the scratchpad (not committed).

- [ ] **Step 1: Device tests**

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: all device tests PASS (3 EPUB host + 6 handle + 3 engine).

- [ ] **Step 2: Install and open the sample books**

```bash
./gradlew :app:installDebug
adb shell ls /sdcard/Download/   # epub_tech, pdf_latex, pdf_scan_vi, pdf_calibre_vi, pdf_comic, pdf_slides, pdf_indesign
```

If the library on the emulator is empty, add the seven sample files with the + button (multi-select in Downloads).

- [ ] **Step 3: Measure criterion 3 (scanned textbook)**

Force-stop the app, open the scanned book from the Library, and time the first page:

```bash
adb shell am force-stop dev.kent.windbook
adb logcat -c
# open the book in the UI, then:
adb shell dumpsys meminfo dev.kent.windbook | grep "TOTAL PSS"
```

Scroll down about 30 pages (swipe or volume-down ×30), then run `dumpsys meminfo` again. Record the time to first page (stopwatch or screen recording) and both PSS values. Pass: first page ≤ 1.5 s and PSS < 400 MB. If either fails, use superpowers:systematic-debugging before continuing, for example by checking that `PdfPages` cache entries are evicted and every page is closed.

- [ ] **Step 4: Screenshots and criteria 1, 2, 4, 5**

For each sample, take `adb exec-out screencap -p > $SCRATCH/pdf-after-<name>.png` at a body page. Compare with the "before" shots in the research note.

- **LaTeX:** body text height at least 1.3× the "before" shot (criterion 1). No horizontal scrolling at 1× (criterion 2).
- **Comic:** View → Page by page. Close and reopen the book: it opens page by page (criterion 4).
- **Slides:** open page by page by default.
- **Reopen:** an M1-era PDF reopens on the same page (criterion 5).
- **EPUB:** first line clear of the top edge.
- **Library:** the slides cover shows whole.

- [ ] **Step 5: Docs**

Append to `docs/testing/m1-manual-checklist.md`:

```markdown
## PDF display (2026-10-15)

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| P1 | Open a portrait PDF (LaTeX/InDesign book) | Fills the width, white margins cropped, scrolls vertically, no sideways panning | | |
| P2 | Pinch to 3×, scroll down, wait 0.2 s | Text sharp; no sideways jumps when scrolling down | | |
| P3 | Open the scanned textbook, scroll 30 pages | First page ≤ 1.5 s; `dumpsys meminfo` TOTAL PSS < 400 MB | | |
| P4 | Comic PDF → View → Page by page; reopen | Opens page by page next time; edge taps turn pages | | |
| P5 | Slides PDF | Opens page by page, whole slide visible | | |
| P6 | View → Crop margins off | Whole page with margins; choice remembered | | |
| P7 | EPUB | First and last lines clear of the screen edges and camera | | |
```

Add to the roadmap table after the Look & feel row:

```markdown
| PDF display | `2026-10-15-windbook-pdf-display.md` | Look & feel | Own PDF renderer (framework PdfRenderer), auto-cropped margins, vertical scroll / page by page per book, `book_preferences` (schema 2), EPUB edge spacing, landscape covers | Done |
```

In `docs/research/2026-10-15-library-analysis.md`, add an "After (2026-10-15 PDF display)" table with the numbers measured in Steps 3–4.

- [ ] **Step 6: Full CI check and commit**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add docs
git commit -m "docs: PDF display checks, measurements and roadmap entry"
```
