# WindBook PDF Comfort Implementation Plan

> Historical implementation plan. Reflow was removed by owner decision on
> 2026-10-20. Do not reimplement its tasks; see
> [Remove PDF Reflow](../specs/2026-10-20-remove-pdf-reflow-design.md).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Large scanned PDFs import in about a second instead of 36 s. A book zoomed once reopens at its zoom. A new Reflow mode re-wraps the words of any PDF page (scans included) at a readable size.

**Architecture:**
- **Import.** Readium keeps reading PDF title and author through a factory whose documents have no cover. WindBook renders a 600 px cover with the framework `PdfRenderer`.
- **Zoom.** Zoom and horizontal position are two more fields in the per-book `book_preferences` JSON, read once by `PdfReaderView` and reported back after a gesture settles.
- **Reflow.** Pure Kotlin in `:core:model`. `PageAnalyzer` turns a page's luminance into lines, words and figures. `ReflowLayouter` places the word images into rows. `:reader` caches the analysis per page. `:app` draws the pieces on a `Canvas` per page in a `LazyColumn`.

**Tech Stack:** Kotlin 2.4.20, Compose BOM 2026.09.00, `android.graphics.pdf.PdfRenderer`, Readium 3.4.0 (`PdfDocumentFactory` / `PdfDocument` interfaces), Room 3 JSON column, JUnit 4, Robolectric 4.17, Compose UI test, AndroidX Test on emulator `Neo3GT_API_36`.

**Spec:** `docs/superpowers/specs/2026-10-15-windbook-pdf-comfort-design.md`

## Global Constraints

- minSdk 31, targetSdk 36, compileSdk 37. Bytecode Java 11. Package root `dev.kent.windbook`.
- **No new Gradle dependency.** PdfiumAndroid never renders; WindBook renders with `PdfRenderer` only.
- Every user-visible string in `values/strings.xml` **and** `values-vi/strings.xml`.
- Reflow text sizes (on-screen line height): 18, 21, 24, 28, 32, 38, 44 dp. The default is 24 dp (level 2).
- JSON keys in `book_preferences`: `pdfLayout` (`VERTICAL` | `PAGED` | `REFLOW`), `cropMargins`, `pdfZoom`, `pdfZoomX`, `pdfReflowSize`. Unknown keys are kept on write. No Room migration.
- A book never zoomed opens at 1× fit-width with no sideways panning.
- The reflow source bitmap is at most 2× the viewport width. Bitmaps go through the existing `PdfPages` LRU.
- The analysis render is 1200 px wide.
- Commits use conventional prefixes. **No `Co-Authored-By` trailer and no Claude attribution** (owner rule).
- CI check: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug`. Device check: `./gradlew :reader:connectedDebugAndroidTest` on `Neo3GT_API_36` (start it with `QT_QPA_PLATFORM=xcb setsid nohup ~/Android/Sdk/emulator/emulator -avd Neo3GT_API_36 -no-snapshot-load -cores 4 -gpu swiftshader_indirect &`).

## Review Focus

1. **A page that is one photo or one big table, in Reflow,** shows whole, never sliced into "words". Tests: `PageAnalyzerTest.fullBleedPhotoIsOneFigure` (Task 5), `PdfReaderViewTest.reflowFallsBackToThePageWhenThereIsNoLayout` (Task 8).
2. **Vietnamese lines that touch at 1.2 leading** become two lines, not one double-height line with interleaved words. Test: `PageAnalyzerTest.touchingLinesAreSplitAtTheLightestRow` (Task 5).
3. **Switching Vertical → Reflow** keeps the page being read. Test: `PdfReaderViewTest.switchingToReflowKeepsThePage` (Task 8).
4. **Out-of-range saved values** (zoom 9, zoomX −1, reflow size 42, from a hand edit or a later version) open safely clamped. Test: `PdfDisplayTest.outOfRangeSavedValuesAreClamped` (Task 2).
5. **A PDF whose first page can't be rendered** still imports, just without a cover. Test: `PdfImportDeviceTest.aPdfWhoseCoverCannotRenderGivesNoCover` (Task 1).

---

## File Structure

```
reader/src/main/kotlin/dev/kent/windbook/reader/
    NoCoverPdfFactory.kt          Readium PdfDocumentFactory wrapper, cover() = null                      (Task 1)
    ReadiumServices.kt            wraps its pdfFactory                                                      (Task 1)
    ReadiumMetadataExtractor.kt   PDF cover via PdfDocumentHandle; pdfCoverWebp()                          (Task 1)
    pdf/PdfDocumentHandle.kt      luminance(page, widthPx, crop)                                           (Task 7)
    pdf/PdfPageSource.kt          + pageLayout(page, crop) default null                                    (Task 7)
    pdf/PdfPages.kt               analysis cache                                                           (Task 7)
core/model/src/main/kotlin/dev/kent/windbook/core/model/
    PdfDisplay.kt                 BookDisplayPrefs/EffectiveDisplay fields, ReflowSizes, REFLOW            (Tasks 2, 8)
    PageLayout.kt                 PageRect, LayoutBlock, PageLayout                                        (Task 5)
    PageAnalyzer.kt               §4.1                                                                     (Task 5)
    ReflowLayouter.kt             ReflowPiece, ReflowPage, ReflowLayouter                                  (Task 6)
core/data/src/main/kotlin/dev/kent/windbook/core/data/
    DisplayPreferencesRepository.kt  codec keys                                                            (Task 2)
app/src/main/kotlin/dev/kent/windbook/
    reader/pdf/PdfReaderView.kt   zoom memory + reading-order keys (Task 3); Reflow view (Task 8)
    reader/ReaderViewModel.kt     setPdfZoom (Task 4); setReflowSize (Task 9)
    reader/ReaderScreen.kt        route wiring (Tasks 4, 9); View menu Reflow + A−/A+ (Task 9)
app/src/main/res/values{,-vi}/strings.xml                                                                  (Task 9)
```

---

### Task 1: Light PDF import

**Files:**
- Create: `reader/src/main/kotlin/dev/kent/windbook/reader/NoCoverPdfFactory.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReadiumServices.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/ReadiumMetadataExtractor.kt`
- Test: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/PdfImportDeviceTest.kt`

**Interfaces:**
- Consumes: `PdfDocumentHandle.open(file)`, `pageSize(0)`, `render(page, crop, widthPx)`, `CropBox.FULL` (existing). `PdfFixtures.boxed(dir, name, pages, pageW, pageH)` (existing androidTest fixture).
- Produces:
  - `internal class NoCoverPdfFactory(inner: PdfDocumentFactory<*>) : PdfDocumentFactory<NoCoverPdfDocument>`
  - `internal suspend fun pdfCoverWebp(file: File): ByteArray?`

- [ ] **Step 1: Write the failing timing test**

`reader/src/androidTest/kotlin/dev/kent/windbook/reader/PdfImportDeviceTest.kt`:

```kotlin
package dev.kent.windbook.reader

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.pdf.PdfFixtures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfImportDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Readium rendered a full-size cover on import: ~36 s and ~1.5 GB for the owner's 2654×4329 pt scan. */
    @Test fun largeScannedPagesImportQuicklyWithASmallCover() = runBlocking {
        val file = PdfFixtures.boxed(context.cacheDir, "import-huge.pdf", pages = 3, pageW = 2654, pageH = 4329)
        val started = SystemClock.elapsedRealtime()
        val meta = ReadiumMetadataExtractor(ReadiumServices(context)).extract(file, BookFormat.PDF).getOrThrow()
        val elapsed = SystemClock.elapsedRealtime() - started
        assertTrue("import took $elapsed ms", elapsed < 1500)
        val bytes = meta.coverWebp!!
        val cover = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertTrue("cover ${cover.width}×${cover.height}", cover.height in 1..600)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :reader:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.kent.windbook.reader.PdfImportDeviceTest`
Expected: FAIL with `import took ~8000 ms` (Readium renders a ~15 000×25 000 px cover).

- [ ] **Step 3: Add the cover-failure test**

Append inside the class, and add the import `org.junit.Assert.assertNull`:

```kotlin
    @Test fun aPdfWhoseCoverCannotRenderGivesNoCover() = runBlocking {
        val junk = context.cacheDir.resolve("no-cover.pdf").apply { writeText("%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n") }
        assertNull(pdfCoverWebp(junk))
    }
```

Run: `./gradlew :reader:compileDebugAndroidTestKotlin`
Expected: compilation FAILS (`Unresolved reference: pdfCoverWebp`).

- [ ] **Step 4: Factory without covers**

`reader/src/main/kotlin/dev/kent/windbook/reader/NoCoverPdfFactory.kt`:

```kotlin
package dev.kent.windbook.reader

import android.content.Context
import android.graphics.Bitmap
import kotlin.reflect.KClass
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.pdf.PdfDocument
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.resource.Resource

/**
 * Readium's PDF parser renders a cover at the first page's full device-pixel size when it opens a PDF
 * (≈ 15 500 × 25 000 px, 1.5 GB, for the owner's scanned textbook). Documents from this factory have no
 * cover, so Readium only reads metadata; WindBook renders a small cover itself (pdfCoverWebp).
 */
internal class NoCoverPdfFactory(private val inner: PdfDocumentFactory<*>) : PdfDocumentFactory<NoCoverPdfDocument> {
    override val documentType: KClass<NoCoverPdfDocument> = NoCoverPdfDocument::class

    override suspend fun open(resource: Resource, password: String?): Try<NoCoverPdfDocument, ReadError> =
        inner.open(resource, password).map { NoCoverPdfDocument(it) }
}

internal class NoCoverPdfDocument(private val inner: PdfDocument) : PdfDocument by inner {
    override suspend fun cover(context: Context): Bitmap? = null
}
```

In `ReadiumServices.kt`, change the parser's factory argument from `pdfFactory = pdfFactory,` to:

```kotlin
            pdfFactory = pdfFactory?.let(::NoCoverPdfFactory),
```

- [ ] **Step 5: Own PDF cover in the extractor**

Replace the whole of `ReadiumMetadataExtractor.kt` with:

```kotlin
package dev.kent.windbook.reader

import android.graphics.Bitmap
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.cover

private const val COVER_HEIGHT = 600

class ReadiumMetadataExtractor(private val services: ReadiumServices) : MetadataExtractor {

    override suspend fun extract(file: File, format: BookFormat): Result<ExtractedMetadata> =
        withContext(Dispatchers.IO) {
            services.openPublication(file).mapCatching { publication ->
                try {
                    ExtractedMetadata(
                        title = publication.metadata.title,
                        author = publication.metadata.authors.firstOrNull()?.name,
                        coverWebp = when (format) {
                            BookFormat.PDF -> pdfCoverWebp(file)
                            BookFormat.EPUB -> publication.cover()?.toWebp()
                        },
                    )
                } finally {
                    publication.close()
                }
            }
        }
}

/** Page 1 at cover height through the framework renderer; null when the page can't be rendered. */
internal suspend fun pdfCoverWebp(file: File): ByteArray? =
    try {
        PdfDocumentHandle.open(file).use { doc ->
            val size = doc.pageSize(0)
            val width = (COVER_HEIGHT * size.width / size.height).roundToInt().coerceAtLeast(1)
            doc.render(0, CropBox.FULL, width).toWebp()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

private fun Bitmap.toWebp(maxHeight: Int = COVER_HEIGHT): ByteArray {
    val scaled = if (height > maxHeight) {
        val width = (width * maxHeight.toFloat() / height).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(this, width, maxHeight, true)
    } else {
        this
    }
    return ByteArrayOutputStream().use { out ->
        scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)
        out.toByteArray()
    }
}
```

- [ ] **Step 6: Run the tests**

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: all device tests PASS (14 existing + 2 `PdfImportDeviceTest`).

Run: `./gradlew :reader:testDebugUnitTest`
Expected: PASS (EPUB metadata extraction in `ReadiumEngineTest` unchanged).

- [ ] **Step 7: Commit**

```bash
git add reader/src
git commit -m "fix(reader): PDF import no longer renders a full-size cover (36 s, 1.5 GB on large scans)"
```

---

### Task 2: Display preferences for zoom and reflow size

**Files:**
- Modify: `core/model/src/main/kotlin/dev/kent/windbook/core/model/PdfDisplay.kt`
- Modify: `core/data/src/main/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepository.kt` (`BookPreferencesCodec`)
- Test: `core/model/src/test/kotlin/dev/kent/windbook/core/model/PdfDisplayTest.kt`, `core/data/src/test/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: existing `BookDisplayPrefs`, `EffectiveDisplay`, `resolve`.
- Produces:
  - `data class BookDisplayPrefs(pdfLayout: PdfLayout? = null, cropMargins: Boolean? = null, zoom: Float? = null, zoomX: Float? = null, reflowSize: Int? = null)`
  - `data class EffectiveDisplay(layout: PdfLayout, cropMargins: Boolean, zoom: Float = 1f, zoomX: Float = 0f, reflowSize: Int = ReflowSizes.DEFAULT)`
  - `const val MAX_PDF_ZOOM = 4f`
  - `object ReflowSizes { val LINE_HEIGHTS_DP: List<Int>; const val DEFAULT = 2; const val LAST = 6; fun lineHeightDp(level: Int): Int }`

- [ ] **Step 1: Write the failing tests**

Append inside `PdfDisplayTest`:

```kotlin
    @Test fun savedZoomAndSizeAreKept() =
        assertEquals(
            EffectiveDisplay(PdfLayout.VERTICAL, true, zoom = 2.5f, zoomX = 0.4f, reflowSize = 5),
            BookDisplayPrefs(zoom = 2.5f, zoomX = 0.4f, reflowSize = 5).resolve(listOf(portrait)),
        )

    @Test fun neverZoomedOpensAtFitWidth() {
        val display = BookDisplayPrefs().resolve(listOf(portrait))
        assertEquals(1f, display.zoom)
        assertEquals(0f, display.zoomX)
        assertEquals(ReflowSizes.DEFAULT, display.reflowSize)
    }

    @Test fun outOfRangeSavedValuesAreClamped() =
        assertEquals(
            EffectiveDisplay(PdfLayout.VERTICAL, true, zoom = MAX_PDF_ZOOM, zoomX = 0f, reflowSize = ReflowSizes.LAST),
            BookDisplayPrefs(zoom = 9f, zoomX = -1f, reflowSize = 42).resolve(listOf(portrait)),
        )

    @Test fun reflowSizesAreTheSpecLineHeights() {
        assertEquals(listOf(18, 21, 24, 28, 32, 38, 44), ReflowSizes.LINE_HEIGHTS_DP)
        assertEquals(24, ReflowSizes.lineHeightDp(ReflowSizes.DEFAULT))
        assertEquals(44, ReflowSizes.lineHeightDp(99))
    }
```

Append inside `DisplayPreferencesRepositoryTest`:

```kotlin
    @Test fun zoomAndReflowSizeRoundTrip() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.VERTICAL, true, zoom = 2.5f, zoomX = 0.4f, reflowSize = 5))
        assertEquals(BookDisplayPrefs(PdfLayout.VERTICAL, true, 2.5f, 0.4f, 5), repo.observe("b").first())
    }

    @Test fun preferencesSavedBeforeZoomExistedHaveNoZoom() = runTest {
        db.bookPreferencesDao().upsert(BookPreferencesEntity("b", """{"pdfLayout":"PAGED","cropMargins":false}""", 1))
        assertEquals(BookDisplayPrefs(PdfLayout.PAGED, false), repo.observe("b").first())
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:model:test --tests 'dev.kent.windbook.core.model.PdfDisplayTest'`
Expected: compilation FAILS (`No parameter with name 'zoom'`, `Unresolved reference: ReflowSizes`).

- [ ] **Step 3: Model**

In `PdfDisplay.kt`, replace the `BookDisplayPrefs` and `EffectiveDisplay` declarations and `resolve` with:

```kotlin
/** Highest zoom a PDF page can be pinched to. */
const val MAX_PDF_ZOOM = 4f

/** Reflow text sizes as on-screen line height in dp (spec §4.3). */
object ReflowSizes {
    val LINE_HEIGHTS_DP = listOf(18, 21, 24, 28, 32, 38, 44)
    const val DEFAULT = 2
    const val LAST = 6

    fun lineHeightDp(level: Int): Int = LINE_HEIGHTS_DP[level.coerceIn(0, LAST)]
}

/**
 * Per-book display choices; null means "use the default". [zoom] and [zoomX] are the vertical-mode zoom and
 * horizontal scroll (0..1 of the scrollable width); [reflowSize] indexes [ReflowSizes.LINE_HEIGHTS_DP].
 */
data class BookDisplayPrefs(
    val pdfLayout: PdfLayout? = null,
    val cropMargins: Boolean? = null,
    val zoom: Float? = null,
    val zoomX: Float? = null,
    val reflowSize: Int? = null,
)

data class EffectiveDisplay(
    val layout: PdfLayout,
    val cropMargins: Boolean,
    val zoom: Float = 1f,
    val zoomX: Float = 0f,
    val reflowSize: Int = ReflowSizes.DEFAULT,
)
```

and

```kotlin
/** Margins are cropped by default except page by page; saved values are clamped to what the view supports. */
fun BookDisplayPrefs.resolve(firstPages: List<PageSize>): EffectiveDisplay {
    val layout = pdfLayout ?: defaultPdfLayout(firstPages)
    return EffectiveDisplay(
        layout = layout,
        cropMargins = cropMargins ?: (layout != PdfLayout.PAGED),
        zoom = (zoom?.takeIf { it.isFinite() } ?: 1f).coerceIn(1f, MAX_PDF_ZOOM),
        zoomX = (zoomX?.takeIf { it.isFinite() } ?: 0f).coerceIn(0f, 1f),
        reflowSize = (reflowSize ?: ReflowSizes.DEFAULT).coerceIn(0, ReflowSizes.LAST),
    )
}
```

- [ ] **Step 4: Codec**

Replace `BookPreferencesCodec` in `DisplayPreferencesRepository.kt` with:

```kotlin
/** JSON so later milestones can add per-book fields without another migration; unknown keys are kept on write. */
internal object BookPreferencesCodec {
    private const val LAYOUT = "pdfLayout"
    private const val CROP = "cropMargins"
    private const val ZOOM = "pdfZoom"
    private const val ZOOM_X = "pdfZoomX"
    private const val REFLOW_SIZE = "pdfReflowSize"

    fun decode(json: String?): BookDisplayPrefs {
        val obj = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return BookDisplayPrefs()
        val layout = obj.optString(LAYOUT).takeIf { it.isNotEmpty() && !obj.isNull(LAYOUT) }
            ?.let { runCatching { PdfLayout.valueOf(it) }.getOrNull() }
        val crop = if (obj.present(CROP)) obj.optBoolean(CROP) else null
        return BookDisplayPrefs(
            pdfLayout = layout,
            cropMargins = crop,
            zoom = obj.floatOrNull(ZOOM),
            zoomX = obj.floatOrNull(ZOOM_X),
            reflowSize = if (obj.present(REFLOW_SIZE)) obj.optInt(REFLOW_SIZE) else null,
        )
    }

    fun encode(previous: String?, prefs: BookDisplayPrefs): String {
        val obj = previous?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        obj.put(LAYOUT, prefs.pdfLayout?.name ?: JSONObject.NULL)
        obj.put(CROP, prefs.cropMargins ?: JSONObject.NULL)
        obj.put(ZOOM, prefs.zoom?.toDouble() ?: JSONObject.NULL)
        obj.put(ZOOM_X, prefs.zoomX?.toDouble() ?: JSONObject.NULL)
        obj.put(REFLOW_SIZE, prefs.reflowSize ?: JSONObject.NULL)
        return obj.toString()
    }

    private fun JSONObject.present(key: String) = has(key) && !isNull(key)

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (present(key)) optDouble(key).takeIf { it.isFinite() }?.toFloat() else null
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest`
Expected: PASS, including the 4 new `PdfDisplayTest` and 2 new `DisplayPreferencesRepositoryTest` tests.

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.*'`
Expected: PASS (existing `EffectiveDisplay(layout, crop)` call sites keep their meaning through the defaults).

- [ ] **Step 6: Commit**

```bash
git add core/model/src core/data/src
git commit -m "feat(model): per-book PDF zoom, horizontal position and reflow size"
```

---

### Task 3: Remembered zoom and reading-order keys in the vertical view

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt` (`PdfReaderView`, `VerticalPages`)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfReaderViewTest.kt`

**Interfaces:**
- Consumes: `MAX_PDF_ZOOM` (Task 2).
- Produces: `PdfReaderView(…existing…, modifier: Modifier = Modifier, initialZoom: Float = 1f, initialZoomX: Float = 0f, onZoomChanged: (zoom: Float, zoomX: Float) -> Unit = { _, _ -> })`. `initialZoom`/`initialZoomX` are read once when the vertical view enters composition. `onZoomChanged` fires 500 ms after zoom or horizontal scroll settles, only when either moved by more than 0.01.

- [ ] **Step 1: Write the failing tests**

In `PdfReaderViewTest`:
- In `FakeSource`, add the field `val regions = mutableListOf<Pair<Int, PixelRegion>>()`. In its `render`, add `if (region != null) regions += widthPx to region` as the first line.
- Add the field `private val zooms = mutableListOf<Pair<Float, Float>>()`.
- Replace the `show` helper with:

```kotlin
    private fun show(
        source: PdfPageSource = book,
        layout: PdfLayout,
        initialPage: Int = 0,
        cropMargins: Boolean = false,
        initialZoom: Float = 1f,
        initialZoomX: Float = 0f,
    ) {
        compose.setContent {
            CompositionLocalProvider(LocalKeyEventDispatcher provides dispatcher) {
                PdfReaderView(
                    source = source, layout = layout, cropMargins = cropMargins, initialPage = initialPage,
                    onPageChanged = { pages += it }, onCenterTap = { taps++ },
                    initialZoom = initialZoom, initialZoomX = initialZoomX,
                    onZoomChanged = { zoom, x -> zooms += zoom to x },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }
```

- Append these tests:

```kotlin
    @Test fun restoresTheSavedZoomAndHorizontalPosition() {
        show(layout = PdfLayout.VERTICAL, initialZoom = 2f, initialZoomX = 0.5f)
        settle()
        val (zoomedWidth, region) = book.regions.last()
        val viewport = zoomedWidth / 2
        // Half of the scrollable width, which is one viewport at 2×.
        assertTrue("tile at x=${region.x}, viewport $viewport", abs(region.x - viewport / 2) <= 2)
        assertTrue("an unchanged zoom is not saved again: $zooms", zooms.isEmpty())
    }

    @Test fun zoomingReportsTheZoomForTheBook() {
        show(layout = PdfLayout.VERTICAL)
        compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { doubleClick(center) }
        settle()
        val (zoom, x) = zooms.last()
        assertEquals(2.5f, zoom, 0.01f)
        assertTrue("zoomX $x", x in 0f..1f)
    }

    @Test fun volumeKeysReadAZoomedPageInReadingOrder() {
        show(layout = PdfLayout.VERTICAL, initialZoom = 2f)
        fun press() {
            pressVolumeDown()
            settle()
        }
        press()
        assertEquals(0.9f, zooms.last().second, 0.02f) // right by 90 % of the screen
        press()
        assertEquals(1f, zooms.last().second, 0.02f) // stops at the right edge
        press()
        assertEquals(0f, zooms.last().second, 0.02f) // back to the left edge, one screen down
        repeat(6) { press() }
        assertTrue("pages: $pages", pages.last() >= 1)
    }
```

Add the imports `kotlin.math.abs`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfReaderViewTest'`
Expected: compilation FAILS (`No parameter with name 'initialZoom'`).

- [ ] **Step 3: Implement**

In `PdfReaderView.kt`:

- Add the parameters after `modifier`:

```kotlin
    modifier: Modifier = Modifier,
    initialZoom: Float = 1f,
    initialZoomX: Float = 0f,
    onZoomChanged: (zoom: Float, zoomX: Float) -> Unit = { _, _ -> },
) {
```

- Change the `VERTICAL` branch to:

```kotlin
        PdfLayout.VERTICAL -> VerticalPages(
            source, currentPage, cropFor, resolveCrop, report, onCenterTap, modifier,
            initialZoom, initialZoomX, onZoomChanged,
        )
```

- Add the same three parameters to the end of `VerticalPages`'s parameter list (`initialZoom: Float, initialZoomX: Float, onZoomChanged: (Float, Float) -> Unit`).
- In `VerticalPages`, replace `var zoom by remember { mutableFloatStateOf(1f) }` with:

```kotlin
    // Read once: later saves come back through the same parameters and must not reset a gesture.
    var zoom by remember { mutableFloatStateOf(initialZoom.coerceIn(1f, MAX_PDF_ZOOM)) }
    val reportZoom by rememberUpdatedState(onZoomChanged)
```

- After the existing `LaunchedEffect(listState) { … onPageChanged … }` block, add:

```kotlin
    // Back to the saved horizontal position once the zoomed width is known.
    LaunchedEffect(Unit) {
        if (zoom > 1f && initialZoomX > 0f) {
            val max = snapshotFlow { hScroll.maxValue }.first { it in 1 until Int.MAX_VALUE }
            hScroll.scrollTo((initialZoomX.coerceIn(0f, 1f) * max).roundToInt())
        }
    }

    // Saves the zoom per book (spec §3) once a pinch, double-tap or sideways drag has settled.
    LaunchedEffect(Unit) {
        var saved = zoom to initialZoomX.coerceIn(0f, 1f)
        snapshotFlow { Triple(zoom, hScroll.value, hScroll.isScrollInProgress || pinching) }
            .distinctUntilChanged()
            .collectLatest { (z, value, moving) ->
                if (moving) return@collectLatest
                delay(500)
                val max = hScroll.maxValue
                val x = if (z <= 1f || max <= 0 || max == Int.MAX_VALUE) 0f else value.toFloat() / max
                if (abs(z - saved.first) > 0.01f || abs(x - saved.second) > 0.01f) {
                    saved = z to x
                    reportZoom(z, x)
                }
            }
    }
```

- Replace the `PageTurnKeys { … }` block inside `BoxWithConstraints` with:

```kotlin
        PageTurnKeys { forward ->
            scope.launch {
                if (zoom > 1.01f) {
                    readingOrderStep(forward, hScroll, listState, stepX = (viewportW * 0.9f).roundToInt(), stepY = viewportH * 0.9f)
                } else {
                    listState.animateScrollBy(viewportH * 0.9f * if (forward) 1 else -1)
                }
            }
            true
        }
```

- Add this function below `PageTurnKeys`:

```kotlin
/**
 * Volume keys while zoomed read like a page: across the line, then back to the left edge and down (spec §3).
 * Each step overlaps the previous view by 10 %.
 */
private suspend fun readingOrderStep(forward: Boolean, h: ScrollState, list: LazyListState, stepX: Int, stepY: Float) {
    if (forward) {
        if (h.value < h.maxValue) {
            h.animateScrollTo(minOf(h.value + stepX, h.maxValue))
        } else {
            h.animateScrollTo(0)
            list.animateScrollBy(stepY)
        }
    } else {
        if (h.value > 0) {
            h.animateScrollTo(maxOf(h.value - stepX, 0))
        } else {
            h.animateScrollTo(h.maxValue)
            list.animateScrollBy(-stepY)
        }
    }
}
```

- Add the imports `androidx.compose.foundation.ScrollState`, `androidx.compose.foundation.lazy.LazyListState`, `androidx.compose.runtime.rememberUpdatedState`, `dev.kent.windbook.core.model.MAX_PDF_ZOOM`, `kotlin.math.abs` and `kotlinx.coroutines.flow.first`. Change the `coerceIn(1f, MAX_ZOOM)` in the pinch handler to `coerceIn(1f, MAX_PDF_ZOOM)` and delete the now-unused `private const val MAX_ZOOM = 4f`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.pdf.PdfReaderViewTest'`
Expected: PASS (13 existing + 3 new).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt app/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfReaderViewTest.kt
git commit -m "feat(app): PDF view restores and reports zoom; volume keys read zoomed pages in order"
```

---

### Task 4: Save and restore the zoom per book

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` (`ReaderRoute`'s `PdfReaderView` call)
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelPdfTest.kt`

**Interfaces:**
- Consumes: `BookDisplayPrefs.zoom/zoomX`, `EffectiveDisplay.zoom/zoomX` (Task 2). `PdfReaderView(initialZoom, initialZoomX, onZoomChanged)` (Task 3).
- Produces: `ReaderViewModel.setPdfZoom(zoom: Float, zoomX: Float)`.

- [ ] **Step 1: Write the failing test**

Append inside `ReaderViewModelPdfTest`:

```kotlin
    @Test fun zoomIsSavedForTheBookAndRestored() = runTest {
        val first = vm(PageSize(400f, 600f))
        first.pdfDisplay.filterNotNull().first()
        first.setPdfZoom(2f, 0.4f)
        prefs.observe("a").first { it.zoom == 2f }

        val second = vm(PageSize(400f, 600f))
        val display = second.pdfDisplay.filterNotNull().first { it.zoom == 2f }
        assertEquals(0.4f, display.zoomX, 1e-6f)
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderViewModelPdfTest'`
Expected: compilation FAILS (`Unresolved reference: setPdfZoom`).

- [ ] **Step 3: Implement**

In `ReaderViewModel.kt`, after `setCropMargins`, add:

```kotlin
    fun setPdfZoom(zoom: Float, zoomX: Float) = updatePrefs { it.copy(zoom = zoom, zoomX = zoomX) }
```

In `ReaderScreen.kt` (`ReaderRoute`), add three arguments to the `PdfReaderView(…)` call after `onCenterTap = viewModel::toggleOverlay,`:

```kotlin
                        initialZoom = display.zoom,
                        initialZoomX = display.zoomX,
                        onZoomChanged = viewModel::setPdfZoom,
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/dev/kent/windbook/reader app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelPdfTest.kt
git commit -m "feat(app): each PDF reopens at the zoom it was read at"
```

---

### Task 5: Page analysis (lines, words, figures)

**Files:**
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/PageLayout.kt`
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/PageAnalyzer.kt`
- Test: `core/model/src/test/kotlin/dev/kent/windbook/core/model/PageAnalyzerTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `data class PageRect(left, top, right, bottom: Float)` with `width`, `height`
  - `sealed interface LayoutBlock { data class TextLine(top, bottom, baseline: Float, words: List<PageRect>, startsParagraph: Boolean); data class Figure(box: PageRect) }`
  - `data class PageLayout(blocks: List<LayoutBlock>, aspect: Float, medianLineHeight: Float) { val textLines: List<LayoutBlock.TextLine> }`. Coordinates are fractions of the analysed area: x of its width, y of its height. `aspect` = height / width. `medianLineHeight` is a fraction of the height, 0 without text.
  - `object PageAnalyzer { fun analyze(luma: ByteArray, width: Int, height: Int): PageLayout }`. Luminance is row-major, 0 = black, 255 = white.

- [ ] **Step 1: Write the failing tests**

`core/model/src/test/kotlin/dev/kent/windbook/core/model/PageAnalyzerTest.kt`:

```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageAnalyzerTest {
    /** A synthetic 600×800 page; letters are 8×12 hollow boxes (1 px stroke) 2 px apart, words 10 px apart. */
    private class Page(val w: Int = 600, val h: Int = 800, paper: Int = 255) {
        val px = ByteArray(w * h) { paper.toByte() }

        fun rect(l: Int, t: Int, r: Int, b: Int, ink: Int = 0) {
            for (y in t until b) for (x in l until r) px[y * w + x] = ink.toByte()
        }

        private fun letter(x: Int, top: Int, ink: Int) {
            rect(x, top, x + 8, top + 1, ink)
            rect(x, top + 11, x + 8, top + 12, ink)
            rect(x, top, x + 1, top + 12, ink)
            rect(x + 7, top, x + 8, top + 12, ink)
        }

        /** Words of the given letter counts from [x]; returns the right edge (exclusive). */
        fun line(x: Int, top: Int, words: List<Int>, ink: Int = 0): Int {
            var cx = x
            for (n in words) {
                repeat(n) { letter(cx, top, ink); cx += 10 }
                cx += 8 // the last letter's 2 px gap + 8 = a 10 px word gap
            }
            return cx - 10
        }

        fun analyze() = PageAnalyzer.analyze(px, w, h)
    }

    @Test fun findsLinesAndWordsInReadingOrder() {
        val page = Page()
        for (i in 0 until 5) page.line(40, 100 + i * 20, listOf(3, 4, 2))
        val lines = page.analyze().textLines
        assertEquals(5, lines.size)
        assertTrue(lines.all { it.words.size == 3 })
        assertEquals(40 / 600f, lines[0].words[0].left, 1e-4f)
        assertEquals(68 / 600f, lines[0].words[0].right, 1e-4f)
        assertEquals(lines.map { it.top }.sorted(), lines.map { it.top })
    }

    @Test fun detachedMarkJoinsTheLineBelow() {
        val page = Page()
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(3, 4, 2))
        page.rect(40, 114, 43, 117) // a tone mark 3 px above line 2 (2 px below line 1)
        val lines = page.analyze().textLines
        assertEquals(3, lines.size)
        assertEquals(114 / 800f, lines[1].top, 1e-4f)
    }

    @Test fun twoColumnsReadLeftThenRight() {
        val page = Page()
        for (i in 0 until 10) {
            page.line(40, 100 + i * 20, listOf(5, 5, 5))
            page.line(330, 100 + i * 20, listOf(5, 5, 5))
        }
        val lines = page.analyze().textLines
        assertEquals(20, lines.size)
        assertTrue(lines.take(10).all { it.words.first().left < 0.5f })
        assertTrue(lines.drop(10).all { it.words.first().left > 0.5f })
    }

    @Test fun figureBlockStaysWhole() {
        val page = Page()
        page.line(40, 100, listOf(5, 5, 5))
        page.line(40, 120, listOf(5, 5, 5))
        page.rect(40, 150, 300, 300)
        page.line(40, 320, listOf(5, 5, 5))
        page.line(40, 340, listOf(5, 5, 5))
        val blocks = page.analyze().blocks
        assertEquals(listOf("T", "T", "F", "T", "T"), blocks.map { if (it is LayoutBlock.Figure) "F" else "T" })
        val figure = (blocks[2] as LayoutBlock.Figure).box
        assertEquals(40 / 600f, figure.left, 1e-3f)
        assertEquals(150 / 800f, figure.top, 1e-3f)
        assertEquals(300 / 600f, figure.right, 1e-3f)
        assertEquals(300 / 800f, figure.bottom, 1e-3f)
    }

    @Test fun indentStartsAParagraph() {
        val page = Page()
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(5, 5, 5))
        page.line(70, 160, listOf(5, 5, 4))
        val starts = page.analyze().textLines.map { it.startsParagraph }
        assertEquals(listOf(false, false, false, true), starts)
    }

    @Test fun largeGapStartsAParagraph() {
        val page = Page()
        for (top in listOf(100, 120, 140, 180)) page.line(40, top, listOf(5, 5, 5))
        assertEquals(listOf(false, false, false, true), page.analyze().textLines.map { it.startsParagraph })
    }

    @Test fun greyScanPaperIsNotInk() {
        val page = Page(paper = 225)
        for (i in 0 until 3) page.line(40, 100 + i * 20, listOf(3, 3), ink = 150)
        assertEquals(3, page.analyze().textLines.size)
    }

    @Test fun blankPageIsOneFigure() =
        assertEquals(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), Page().analyze().blocks)

    @Test fun fullBleedPhotoIsOneFigure() {
        val page = Page()
        for (y in 0 until page.h) for (x in 0 until page.w) page.px[y * page.w + x] = ((x + y) % 256).toByte()
        assertEquals(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), page.analyze().blocks)
    }

    @Test fun touchingLinesAreSplitAtTheLightestRow() {
        val page = Page()
        for (i in 0 until 6) page.line(40, 100 + i * 20, listOf(5, 5, 5))
        page.line(40, 240, listOf(5, 5, 5))
        page.rect(40, 252, 42, 253) // a descender touching the next line
        page.line(40, 253, listOf(5, 5, 5))
        assertEquals(8, page.analyze().textLines.size)
    }

    @Test fun baselineIsTheBottomOfTheLetters() {
        val page = Page()
        page.line(40, 100, listOf(3))
        page.line(40, 120, listOf(3))
        assertEquals(112 / 800f, page.analyze().textLines[0].baseline, 1e-4f)
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:model:test --tests 'dev.kent.windbook.core.model.PageAnalyzerTest'`
Expected: compilation FAILS (`Unresolved reference: PageAnalyzer`, `LayoutBlock`).

- [ ] **Step 3: Layout types**

`core/model/src/main/kotlin/dev/kent/windbook/core/model/PageLayout.kt`:

```kotlin
package dev.kent.windbook.core.model

/** A rectangle in fractions of the analysed area: x of its width, y of its height. */
data class PageRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

sealed interface LayoutBlock {
    /** One source line of text. [baseline] lies between [top] and [bottom]; each word spans the whole line height. */
    data class TextLine(
        val top: Float,
        val bottom: Float,
        val baseline: Float,
        val words: List<PageRect>,
        val startsParagraph: Boolean,
    ) : LayoutBlock

    /** A picture, table or formula kept as one image. */
    data class Figure(val box: PageRect) : LayoutBlock
}

/**
 * One page's crop box in reading order (spec §4.1). [aspect] is height / width of the analysed area and
 * [medianLineHeight] a fraction of its height (0 when the page has no text).
 */
data class PageLayout(val blocks: List<LayoutBlock>, val aspect: Float, val medianLineHeight: Float) {
    val textLines: List<LayoutBlock.TextLine> get() = blocks.filterIsInstance<LayoutBlock.TextLine>()
}
```

- [ ] **Step 4: Analyzer**

`core/model/src/main/kotlin/dev/kent/windbook/core/model/PageAnalyzer.kt`:

```kotlin
package dev.kent.windbook.core.model

import kotlin.math.max
import kotlin.math.roundToInt

/** Finds columns, text lines, words and figures in a page image (spec §4.1). Pure, so it is tested on the JVM. */
object PageAnalyzer {
    const val MIN_GUTTER = 0.03f
    const val GUTTER_SPAN = 0.8f
    const val MIN_COLUMN = 0.2f
    const val MAX_COLUMNS = 3
    const val TALL_LINE = 1.6f
    const val SPLIT_INK = 0.15f
    const val MARK_HEIGHT = 0.5f
    const val MARK_DISTANCE = 0.35f
    const val FIGURE_HEIGHT = 2.5f
    const val FIGURE_PAGE_SHARE = 0.25f
    const val FIGURE_DENSITY = 0.45f
    const val X_HEIGHT_INK = 0.4f
    const val WORD_GAP = 0.35f
    const val PARAGRAPH_GAP = 1.5f
    const val PARAGRAPH_INDENT = 0.04f
    const val SHORT_LINE = 0.8f

    fun analyze(luma: ByteArray, width: Int, height: Int): PageLayout {
        val wholePage = PageLayout(listOf(LayoutBlock.Figure(PageRect(0f, 0f, 1f, 1f))), height.toFloat() / width, 0f)
        val threshold = otsu(luma) ?: return wholePage
        val ink = Ink(luma, width, height, threshold)
        val inkRows = (0 until height).filter { ink.rowCount(it, 0, width - 1) > 0 }
        if (inkRows.isEmpty()) return wholePage
        val blocks = mutableListOf<LayoutBlock>()
        val lineHeights = mutableListOf<Int>()
        for (column in columns(ink, inkRows.first(), inkRows.last())) {
            Column(ink, column).let { blocks += it.blocks; lineHeights += it.textLineHeights }
        }
        if (lineHeights.isEmpty()) return wholePage
        return PageLayout(blocks, height.toFloat() / width, median(lineHeights).toFloat() / height)
    }

    /** Otsu's threshold: luminance below it is ink. Null for a flat page (blank, or one solid tone). */
    internal fun otsu(luma: ByteArray): Int? {
        val hist = IntArray(256)
        for (b in luma) hist[b.toInt() and 0xFF]++
        val lo = hist.indexOfFirst { it > 0 }
        val hi = hist.indexOfLast { it > 0 }
        if (hi - lo < 32) return null
        val total = luma.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var weightBack = 0.0
        var sumBack = 0.0
        var best = -1.0
        var threshold = 128
        for (i in 0..255) {
            weightBack += hist[i]
            if (weightBack == 0.0) continue
            val weightFore = total - weightBack
            if (weightFore == 0.0) break
            sumBack += i * hist[i].toDouble()
            val meanBack = sumBack / weightBack
            val meanFore = (sumAll - sumBack) / weightFore
            val between = weightBack * weightFore * (meanBack - meanFore) * (meanBack - meanFore)
            if (between > best) {
                best = between
                threshold = i
            }
        }
        return threshold + 1
    }

    /** Splits the content at white gutters at least [MIN_GUTTER] wide and [GUTTER_SPAN] tall. */
    private fun columns(ink: Ink, top: Int, bottom: Int): List<IntRange> {
        val left = (0 until ink.width).first { ink.colCount(it, top, bottom) > 0 }
        val right = (0 until ink.width).last { ink.colCount(it, top, bottom) > 0 }
        val span = bottom - top + 1
        val gutters = mutableListOf<IntRange>()
        var x = left
        while (x <= right) {
            if (ink.longestWhiteRun(x, top, bottom) >= GUTTER_SPAN * span) {
                val start = x
                while (x <= right && ink.longestWhiteRun(x, top, bottom) >= GUTTER_SPAN * span) x++
                gutters += start until x
            } else {
                x++
            }
        }
        val minColumn = MIN_COLUMN * (right - left + 1)
        val splits = mutableListOf<Int>()
        for (gutter in gutters.filter { it.count() >= MIN_GUTTER * ink.width }.sortedByDescending { it.count() }) {
            if (splits.size == MAX_COLUMNS - 1) break
            val bounds = (listOf(left, right) + splits + ((gutter.first + gutter.last) / 2)).sorted()
            if (bounds.zipWithNext().all { (a, b) -> b - a >= minColumn }) splits += (gutter.first + gutter.last) / 2
        }
        return (listOf(left) + splits.sorted() + (right + 1)).zipWithNext { a, b -> a until b }
    }

    /** Lines, words, figures and paragraphs of one column. */
    private class Column(private val ink: Ink, private val xs: IntRange) {
        val blocks = mutableListOf<LayoutBlock>()
        val textLineHeights = mutableListOf<Int>()
        private val rowInk = IntArray(ink.height) { ink.rowCount(it, xs.first, xs.last) }

        init {
            val minRowInk = max(1, (0.002f * xs.count()).roundToInt())
            val runs = mutableListOf<IntRange>()
            var y = 0
            while (y < ink.height) {
                if (rowInk[y] >= minRowInk) {
                    val start = y
                    while (y < ink.height && rowInk[y] >= minRowInk) y++
                    runs += start until y
                } else {
                    y++
                }
            }
            if (runs.isNotEmpty()) build(runs)
        }

        private fun build(runs: List<IntRange>) {
            val h = robustMedian(runs.map { it.count() })
            val lines = mergeMarks(runs.flatMap { splitTall(it, h) }, h)
            val isFigure = lines.map { line -> figureLine(line, h) }
            // Consecutive figure lines (gap ≤ h) form one figure; text lines are kept for words and paragraphs.
            val groups = mutableListOf<Pair<Boolean, MutableList<IntRange>>>()
            lines.forEachIndexed { i, line ->
                val last = groups.lastOrNull()
                if (isFigure[i] && last != null && last.first && line.first - last.second.last().last - 1 <= h) {
                    last.second += line
                } else {
                    groups += isFigure[i] to mutableListOf(line)
                }
            }
            val xHeights = groups.filter { !it.first }.flatMap { it.second }.map { xBand(it).let { (a, b) -> b - a + 1 } }
            val wordGap = max(1f, WORD_GAP * (if (xHeights.isEmpty()) h else median(xHeights)))
            val textBlocks = mutableListOf<Pair<IntRange, List<IntRange>>>()
            val order = mutableListOf<Any>() // IntRange for text lines (resolved below), PageRect for figures
            for ((figure, members) in groups) {
                if (figure) {
                    val top = members.first().first
                    val bottom = members.last().last
                    val (l, r) = extent(top..bottom) ?: continue
                    order += PageRect(l.toFloat() / ink.width, top.toFloat() / ink.height, (r + 1f) / ink.width, (bottom + 1f) / ink.height)
                } else {
                    for (line in members) {
                        val words = words(line, wordGap)
                        if (words.isEmpty()) continue
                        textBlocks += line to words
                        order += line
                    }
                }
            }
            val starts = paragraphStarts(order, textBlocks)
            for (item in order) {
                if (item is PageRect) {
                    blocks += LayoutBlock.Figure(item)
                } else {
                    val line = item as IntRange
                    val words = textBlocks.first { it.first == line }.second
                    val top = line.first.toFloat() / ink.height
                    val bottom = (line.last + 1f) / ink.height
                    blocks += LayoutBlock.TextLine(
                        top = top,
                        bottom = bottom,
                        baseline = (xBand(line).second + 1f) / ink.height,
                        words = words.map { PageRect(it.first.toFloat() / ink.width, top, (it.last + 1f) / ink.width, bottom) },
                        startsParagraph = starts.getValue(line),
                    )
                    textLineHeights += line.count()
                }
            }
        }

        /** Touching lines are cut at their lightest inner row when it is nearly empty. */
        private fun splitTall(run: IntRange, h: Int): List<IntRange> {
            if (run.count() <= TALL_LINE * h || run.count() < 5) return listOf(run)
            val peak = run.maxOf { rowInk[it] }
            val lightest = ((run.first + 2)..(run.last - 2)).minByOrNull { rowInk[it] } ?: return listOf(run)
            if (rowInk[lightest] > SPLIT_INK * peak) return listOf(run)
            return splitTall(run.first until lightest, h) + splitTall((lightest + 1)..run.last, h)
        }

        /** Thin runs near a line (detached diacritics) join it; the line below wins ties. */
        private fun mergeMarks(lines: List<IntRange>, h: Int): List<IntRange> {
            val out = lines.toMutableList()
            val reach = MARK_DISTANCE * h
            var i = 0
            while (i < out.size) {
                val line = out[i]
                if (line.count() >= MARK_HEIGHT * h) {
                    i++
                    continue
                }
                val below = out.getOrNull(i + 1)?.let { it.first - line.last - 1 }
                val above = out.getOrNull(i - 1)?.let { line.first - it.last - 1 }
                when {
                    below != null && below <= reach -> {
                        out[i + 1] = line.first..out[i + 1].last
                        out.removeAt(i)
                    }
                    above != null && above <= reach -> {
                        out[i - 1] = out[i - 1].first..line.last
                        out.removeAt(i)
                        i--
                    }
                    else -> i++
                }
            }
            return out
        }

        private fun figureLine(line: IntRange, h: Int): Boolean {
            val (l, r) = extent(line) ?: return true
            val inkSum = line.sumOf { rowInk[it] }
            val density = inkSum.toFloat() / (line.count() * (r - l + 1))
            return line.count() > FIGURE_HEIGHT * h ||
                line.count() > FIGURE_PAGE_SHARE * ink.height ||
                density > FIGURE_DENSITY ||
                line.count() < MARK_HEIGHT * h
        }

        /** First and last rows holding ≥ [X_HEIGHT_INK] of the line's peak row ink: the x-height band. */
        private fun xBand(line: IntRange): Pair<Int, Int> {
            val peak = line.maxOf { rowInk[it] }
            val strong = line.filter { rowInk[it] >= X_HEIGHT_INK * peak }
            return strong.first() to strong.last()
        }

        private fun extent(rows: IntRange): Pair<Int, Int>? {
            val cols = xs.filter { x -> ink.colCount(x, rows.first, rows.last) > 0 }
            return if (cols.isEmpty()) null else cols.first() to cols.last()
        }

        private fun words(line: IntRange, wordGap: Float): List<IntRange> {
            val inkCols = xs.filter { x -> ink.colCount(x, line.first, line.last) > 0 }
            if (inkCols.isEmpty()) return emptyList()
            val words = mutableListOf<IntRange>()
            var start = inkCols.first()
            var prev = start
            for (x in inkCols.drop(1)) {
                if (x - prev - 1 >= wordGap) {
                    words += start..prev
                    start = x
                }
                prev = x
            }
            words += start..prev
            return words
        }

        private fun paragraphStarts(order: List<Any>, text: List<Pair<IntRange, List<IntRange>>>): Map<IntRange, Boolean> {
            if (text.isEmpty()) return emptyMap()
            val lefts = text.map { it.second.first().first }
            val rights = text.map { it.second.last().last }
            val colLeft = percentile(lefts, 0.1f)
            val colWidth = (percentile(rights, 0.9f) - colLeft).coerceAtLeast(1)
            val gaps = text.zipWithNext { a, b -> b.first.first - a.first.last - 1 }.filter { it > 0 }
            val medianGap = if (gaps.isEmpty()) 0 else median(gaps)
            val result = HashMap<IntRange, Boolean>()
            var previous: Pair<IntRange, List<IntRange>>? = null
            var afterFigure = false
            for (item in order) {
                if (item is PageRect) {
                    afterFigure = true
                    continue
                }
                val current = text.first { it.first == item }
                val indent = current.second.first().first - colLeft > PARAGRAPH_INDENT * colWidth
                val prev = previous
                result[current.first] = indent || (prev != null && (
                    afterFigure ||
                        current.first.first - prev.first.last - 1 > PARAGRAPH_GAP * medianGap ||
                        prev.second.last().last < colLeft + SHORT_LINE * colWidth
                    ))
                previous = current
                afterFigure = false
            }
            return result
        }
    }

    private class Ink(luma: ByteArray, val width: Int, val height: Int, threshold: Int) {
        private val bits = BooleanArray(luma.size) { (luma[it].toInt() and 0xFF) < threshold }

        fun rowCount(y: Int, x0: Int, x1: Int): Int {
            var n = 0
            for (x in x0..x1) if (bits[y * width + x]) n++
            return n
        }

        fun colCount(x: Int, y0: Int, y1: Int): Int {
            var n = 0
            for (y in y0..y1) if (bits[y * width + x]) n++
            return n
        }

        fun longestWhiteRun(x: Int, y0: Int, y1: Int): Int {
            var best = 0
            var run = 0
            for (y in y0..y1) {
                if (bits[y * width + x]) {
                    run = 0
                } else {
                    run++
                    if (run > best) best = run
                }
            }
            return best
        }
    }

    private fun median(values: List<Int>): Int = values.sorted()[values.size / 2]

    /** Median of the runs at least half the plain median, so tone marks don't drag it down. */
    private fun robustMedian(values: List<Int>): Int {
        val m = median(values)
        val tall = values.filter { it >= m / 2 }
        return if (tall.isEmpty()) m else median(tall)
    }

    private fun percentile(values: List<Int>, p: Float): Int = values.sorted()[((values.size - 1) * p).roundToInt()]
}
```

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:model:test --tests 'dev.kent.windbook.core.model.PageAnalyzerTest'`
Expected: 11 tests PASS. If a synthetic test fails, debug the analyzer with superpowers:systematic-debugging. Do not loosen the test; the test pages are the spec's cases.

Run: `./gradlew :core:model:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/model/src
git commit -m "feat(model): page analyzer finds columns, lines, words and figures for reflow"
```

---

### Task 6: Reflow layout

**Files:**
- Create: `core/model/src/main/kotlin/dev/kent/windbook/core/model/ReflowLayouter.kt`
- Test: `core/model/src/test/kotlin/dev/kent/windbook/core/model/ReflowLayouterTest.kt`

**Interfaces:**
- Consumes: `PageLayout`, `LayoutBlock`, `PageRect` (Task 5).
- Produces:
  - `data class ReflowPiece(val src: PageRect, val x: Float, val y: Float, val width: Float, val height: Float)`
  - `data class ReflowPage(val height: Float, val pieces: List<ReflowPiece>, val scale: Float)`. `scale` is px per analysed width, i.e. the full crop box would be `scale` px wide.
  - `object ReflowLayouter { fun layout(page: PageLayout, targetWidth: Float, lineHeight: Float): ReflowPage; fun sourceWidth(scale: Float, viewportWidth: Int): Int }`

- [ ] **Step 1: Write the failing tests**

`core/model/src/test/kotlin/dev/kent/windbook/core/model/ReflowLayouterTest.kt`:

```kotlin
package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflowLayouterTest {
    private val aspect = 1.5f

    /** A line at [top] of [words] words, each 0.1 wide; line height 0.015, baseline at 0.012. */
    private fun line(top: Float, words: Int = 5, startsParagraph: Boolean = false, wordWidth: Float = 0.1f) =
        LayoutBlock.TextLine(
            top = top,
            bottom = top + 0.015f,
            baseline = top + 0.012f,
            words = List(words) { i -> PageRect(0.05f + i * (wordWidth + 0.02f), top, 0.05f + i * (wordWidth + 0.02f) + wordWidth, top + 0.015f) },
            startsParagraph = startsParagraph,
        )

    private fun page(vararg blocks: LayoutBlock) = PageLayout(blocks.toList(), aspect, 0.015f)

    private val tenLines = page(*Array(10) { line(0.1f + it * 0.02f) })

    @Test fun noPieceGoesBeyondTheWidth() {
        val result = ReflowLayouter.layout(tenLines, targetWidth = 300f, lineHeight = 24f)
        assertTrue(result.pieces.all { it.x >= 0f && it.x + it.width <= 300.01f })
    }

    @Test fun wordOrderIsKept() {
        val pieces = ReflowLayouter.layout(tenLines, 300f, 24f).pieces
        assertEquals(50, pieces.size)
        val rows = pieces.zipWithNext().all { (a, b) -> b.y > a.y || (b.y == a.y && b.x > a.x) }
        assertTrue(rows)
    }

    @Test fun largerTextMakesMoreRows() {
        val small = ReflowLayouter.layout(tenLines, 300f, 18f).pieces.map { it.y }.distinct().size
        val large = ReflowLayouter.layout(tenLines, 300f, 32f).pieces.map { it.y }.distinct().size
        assertTrue("rows $small vs $large", large > small)
    }

    @Test fun textIsScaledToTheRequestedLineHeight() {
        // Line height 0.015 of a 1.5 aspect area = 0.0225 widths; 24 px per line → 1066.7 px per width.
        val result = ReflowLayouter.layout(tenLines, 300f, 24f)
        assertEquals(24f / 0.0225f, result.scale, 0.1f)
        assertEquals(24f, result.pieces[0].height, 0.01f)
    }

    @Test fun paragraphStartIsIndented() {
        val result = ReflowLayouter.layout(page(line(0.1f, words = 2), line(0.12f, words = 2, startsParagraph = true)), 600f, 24f)
        assertEquals(0f, result.pieces[0].x, 0.01f)
        assertEquals(1.5f * 24f, result.pieces[2].x, 0.01f)
    }

    @Test fun overWideWordIsScaledToFit() {
        val wide = page(line(0.1f, words = 1, wordWidth = 0.5f))
        val piece = ReflowLayouter.layout(wide, 300f, 24f).pieces.single()
        assertEquals(300f, piece.width, 0.01f)
        assertEquals(0f, piece.x, 0.01f)
    }

    @Test fun figureFitsTheWidthAndIsNeverEnlarged() {
        val big = ReflowLayouter.layout(page(line(0.1f), LayoutBlock.Figure(PageRect(0.1f, 0.3f, 0.9f, 0.6f))), 300f, 24f)
        assertEquals(300f, big.pieces.last().width, 0.01f)
        val small = ReflowLayouter.layout(page(line(0.1f), LayoutBlock.Figure(PageRect(0.1f, 0.3f, 0.12f, 0.31f))), 300f, 24f)
        assertEquals(0.02f * small.scale, small.pieces.last().width, 0.01f)
    }

    @Test fun wordsFromLinesWithDifferentMarksShareABaseline() {
        val plain = line(0.1f, words = 1)
        // A line with tone marks starts higher, so its baseline sits further below its top.
        val marked = LayoutBlock.TextLine(0.115f, 0.135f, 0.132f, listOf(PageRect(0.05f, 0.115f, 0.15f, 0.135f)), false)
        val pieces = ReflowLayouter.layout(page(plain, marked), 600f, 24f).pieces
        val scale = 24f / 0.0225f
        val baseline0 = pieces[0].y + (plain.baseline - plain.top) * aspect * scale
        val baseline1 = pieces[1].y + (marked.baseline - marked.top) * aspect * scale
        assertEquals(baseline0, baseline1, 0.01f)
    }

    @Test fun sourceBitmapIsCappedAtTwiceTheScreen() {
        assertEquals(2160, ReflowLayouter.sourceWidth(5000f, 1080))
        assertEquals(900, ReflowLayouter.sourceWidth(900f, 1080))
    }
}
```

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:model:test --tests 'dev.kent.windbook.core.model.ReflowLayouterTest'`
Expected: compilation FAILS (`Unresolved reference: ReflowLayouter`).

- [ ] **Step 3: Implement**

`core/model/src/main/kotlin/dev/kent/windbook/core/model/ReflowLayouter.kt`:

```kotlin
package dev.kent.windbook.core.model

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Copy [src] (fractions of the crop box) to the px rectangle at ([x], [y]) sized [width]×[height]. */
data class ReflowPiece(val src: PageRect, val x: Float, val y: Float, val width: Float, val height: Float)

/** One page re-wrapped to the screen. [scale] is px per analysed width: the crop box would be [scale] px wide. */
data class ReflowPage(val height: Float, val pieces: List<ReflowPiece>, val scale: Float)

/** Places a page's word images into rows of the screen width at the chosen text size (spec §4.2). */
object ReflowLayouter {
    const val LINE_SPACING = 1.3f
    const val WORD_GAP = 0.35f
    const val PARAGRAPH_SPACE = 0.5f
    const val INDENT = 1.5f

    private class Placed(val src: PageRect, val x: Float, val ascent: Float, val width: Float, val height: Float)

    fun layout(page: PageLayout, targetWidth: Float, lineHeight: Float): ReflowPage {
        val scale = if (page.medianLineHeight > 0f) lineHeight / (page.medianLineHeight * page.aspect) else targetWidth
        val pieces = mutableListOf<ReflowPiece>()
        val row = mutableListOf<Placed>()
        var y = 0f
        var x = 0f
        var first = true

        fun flush() {
            if (row.isEmpty()) return
            val ascent = row.maxOf { it.ascent }
            val descent = row.maxOf { it.height - it.ascent }
            for (p in row) pieces += ReflowPiece(p.src, p.x, y + ascent - p.ascent, p.width, p.height)
            y += max(lineHeight * LINE_SPACING, ascent + descent + lineHeight * 0.3f)
            row.clear()
            x = 0f
        }

        for (block in page.blocks) {
            when (block) {
                is LayoutBlock.TextLine -> {
                    if (block.startsParagraph) {
                        flush()
                        if (!first) y += lineHeight * PARAGRAPH_SPACE
                        x = lineHeight * INDENT
                    }
                    first = false
                    val lineHeightPx = (block.bottom - block.top) * page.aspect * scale
                    val ascentPx = (block.baseline - block.top) * page.aspect * scale
                    for (word in block.words) {
                        var width = word.width * scale
                        var height = lineHeightPx
                        var ascent = ascentPx
                        if (width > targetWidth) {
                            val f = targetWidth / width
                            width *= f
                            height *= f
                            ascent *= f
                        }
                        if (x + width > targetWidth) {
                            if (row.isNotEmpty()) flush() else x = 0f
                        }
                        row += Placed(PageRect(word.left, block.top, word.right, block.bottom), x, ascent, width, height)
                        x += width + lineHeight * WORD_GAP
                    }
                }
                is LayoutBlock.Figure -> {
                    flush()
                    if (!first) y += lineHeight * PARAGRAPH_SPACE
                    first = false
                    val natural = block.box.width * scale
                    val f = min(1f, targetWidth / natural)
                    val width = natural * f
                    val height = block.box.height * page.aspect * scale * f
                    pieces += ReflowPiece(block.box, (targetWidth - width) / 2f, y, width, height)
                    y += height + lineHeight * PARAGRAPH_SPACE
                }
            }
        }
        flush()
        return ReflowPage(y, pieces, scale)
    }

    /** Width to render the crop box at for [scale], capped at twice the screen so memory stays bounded. */
    fun sourceWidth(scale: Float, viewportWidth: Int): Int = scale.roundToInt().coerceIn(1, 2 * viewportWidth)
}
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :core:model:test`
Expected: PASS, including 9 `ReflowLayouterTest`.

- [ ] **Step 5: Commit**

```bash
git add core/model/src
git commit -m "feat(model): reflow layouter places word images into rows at the chosen size"
```

---

### Task 7: Page layouts from the PDF

**Files:**
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfDocumentHandle.kt` (`luminance`)
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPageSource.kt`
- Modify: `reader/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfPages.kt`
- Modify: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfFixtures.kt` (+ `textLines`)
- Test: `reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfLayoutDeviceTest.kt`

**Interfaces:**
- Consumes: `PageAnalyzer.analyze`, `PageLayout` (Task 5).
- Produces:
  - `PdfPageSource.pageLayout(page: Int, crop: CropBox): PageLayout?` (default `null`; `PdfPages` returns the cached analysis)
  - `PdfDocumentHandle.luminance(page: Int, widthPx: Int = 300, crop: CropBox = CropBox.FULL): Luma`
  - `const val ANALYSIS_WIDTH = 1200`

- [ ] **Step 1: Fixture and failing device test**

Append inside `object PdfFixtures`:

```kotlin
    /** One page of real text (drawn with the system font, Vietnamese included), one string per line. */
    fun textLines(dir: File, name: String, lines: List<String>, pageW: Int = 400, pageH: Int = 600, textSize: Float = 14f, leading: Float = 24f): File {
        val doc = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.textSize = textSize
        }
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        lines.forEachIndexed { i, text -> page.canvas.drawText(text, 40f, 60f + i * leading, paint) }
        doc.finishPage(page)
        val file = File(dir, name)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
```

`reader/src/androidTest/kotlin/dev/kent/windbook/reader/pdf/PdfLayoutDeviceTest.kt`:

```kotlin
package dev.kent.windbook.reader.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.CropBox
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfLayoutDeviceTest {
    private val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    private val vietnamese = listOf(
        "Người đàn ông lịch lãm biết cách",
        "Thể hiện ước mơ đẹp nhất của mình",
        "Kiếp nghèo, kiếp cầm ca, kiếp đỏ đen",
        "Ở các nước Âu, Mỹ, phương pháp này",
    )

    @Test fun vietnameseTextIsReadAsLinesAndWords() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.textLines(dir, "vi-text.pdf", vietnamese)).use { doc ->
            val layout = PdfPages(doc).pageLayout(0, CropBox.FULL)!!
            assertEquals("lines: ${layout.textLines.map { it.words.size }}", vietnamese.size, layout.textLines.size)
            assertEquals(7, layout.textLines[0].words.size)
        }
    }

    @Test fun layoutsAreCachedPerPageAndCrop() = runBlocking {
        PdfDocumentHandle.open(PdfFixtures.textLines(dir, "vi-cache.pdf", vietnamese)).use { doc ->
            val pages = PdfPages(doc)
            assertSame(pages.pageLayout(0, CropBox.FULL), pages.pageLayout(0, CropBox.FULL))
        }
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :reader:compileDebugAndroidTestKotlin`
Expected: compilation FAILS (`Unresolved reference: pageLayout`).

- [ ] **Step 3: Implement**

In `PdfDocumentHandle.kt`, replace `luminance` with:

```kotlin
    /** Luminance of [crop] of [page] rendered [widthPx] wide (whole page by default). */
    suspend fun luminance(page: Int, widthPx: Int = 300, crop: CropBox = CropBox.FULL): Luma {
        val bitmap = render(page, crop, widthPx)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        bitmap.recycle()
        val luma = ByteArray(pixels.size) { i ->
            val p = pixels[i]
            ((299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000).toByte()
        }
        return Luma(widthPx, pixels.size / widthPx, luma)
    }
```

In `PdfPageSource.kt`, add inside the interface after `render`:

```kotlin
    /** Lines, words and figures of [crop] of [page] for Reflow; null when this source can't analyse pages. */
    suspend fun pageLayout(page: Int, crop: CropBox): PageLayout? = null
```

with the import `dev.kent.windbook.core.model.PageLayout`.

In `PdfPages.kt`:
- Add at file level: `/** Width the crop box is analysed at for Reflow (spec §4.1). */ const val ANALYSIS_WIDTH = 1200`
- Add the field `private val layouts = LruCache<String, PageLayout>(64)`
- Add the override:

```kotlin
    override suspend fun pageLayout(page: Int, crop: CropBox): PageLayout {
        val key = "$page|$crop"
        layouts.get(key)?.let { return it }
        val luma = document.luminance(page, ANALYSIS_WIDTH, crop)
        val layout = withContext(Dispatchers.Default) { PageAnalyzer.analyze(luma.values, luma.width, luma.height) }
        layouts.put(key, layout)
        return layout
    }
```

- Add the imports `dev.kent.windbook.core.model.PageAnalyzer`, `dev.kent.windbook.core.model.PageLayout`, `kotlinx.coroutines.Dispatchers` and `kotlinx.coroutines.withContext`.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: all PASS (16 from Task 1 + 2 `PdfLayoutDeviceTest`). If `vietnameseTextIsReadAsLinesAndWords` reports more lines than strings, a tone mark became its own line. Inspect it with superpowers:systematic-debugging, for example by logging each line's row range against the rendered luminance. Do not change the test strings.

Run: `./gradlew :reader:testDebugUnitTest :app:testDebugUnitTest`
Expected: PASS (fakes inherit the `null` default).

- [ ] **Step 5: Commit**

```bash
git add reader/src
git commit -m "feat(reader): analyse PDF pages for reflow on the PDF thread, cached per page"
```

---

### Task 8: Reflow view

**Files:**
- Modify: `core/model/src/main/kotlin/dev/kent/windbook/core/model/PdfDisplay.kt` (`PdfLayout.REFLOW`)
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/pdf/PdfReaderView.kt`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/pdf/PdfReaderViewTest.kt`, `core/data/src/test/kotlin/dev/kent/windbook/core/data/DisplayPreferencesRepositoryTest.kt`

**Interfaces:**
- Consumes: `PdfPageSource.pageLayout` (Task 7). `ReflowLayouter.layout/sourceWidth`, `ReflowPage` (Task 6). `ReflowSizes` (Task 2). `PageLayout`, `LayoutBlock`, `PageRect` (Task 5).
- Produces:
  - `enum class PdfLayout { VERTICAL, PAGED, REFLOW }`
  - `PdfReaderView(…, reflowSize: Int = ReflowSizes.DEFAULT, onReflowSizeChange: (Int) -> Unit = {})`
  - `const val PDF_REFLOW_TAG = "pdf-reflow"`

- [ ] **Step 1: Write the failing tests**

Append inside `DisplayPreferencesRepositoryTest`:

```kotlin
    @Test fun reflowLayoutRoundTrips() = runTest {
        repo.set("b", BookDisplayPrefs(PdfLayout.REFLOW, null))
        assertEquals(PdfLayout.REFLOW, repo.observe("b").first().pdfLayout)
    }
```

In `PdfReaderViewTest`:
- Add the field `private val sizes = mutableListOf<Int>()`.
- In `show`, add the parameter `reflowSize: Int = ReflowSizes.DEFAULT` and pass `reflowSize = reflowSize, onReflowSizeChange = { sizes += it }` to `PdfReaderView`.
- Add a sample layout and a reflowable source:

```kotlin
    private val sampleLayout = PageLayout(
        blocks = List(3) { i ->
            val top = 0.1f + i * 0.05f
            LayoutBlock.TextLine(
                top = top,
                bottom = top + 0.02f,
                baseline = top + 0.016f,
                words = List(4) { w -> PageRect(0.1f + w * 0.2f, top, 0.25f + w * 0.2f, top + 0.02f) },
                startsParagraph = i == 0,
            )
        },
        aspect = 1.5f,
        medianLineHeight = 0.02f,
    )

    private val reflowable = object : PdfPageSource by FakeSource(List(20) { PageSize(400f, 600f) }) {
        override suspend fun pageLayout(page: Int, crop: CropBox) = sampleLayout
    }
```

- Append the tests:

```kotlin
    @Test fun reflowShowsLaidOutPagesAndScrollsWithTheVolumeKey() {
        show(source = reflowable, layout = PdfLayout.REFLOW)
        compose.onNodeWithTag(PDF_REFLOW_TAG).assertExists()
        repeat(5) { pressVolumeDown() }
        assertTrue("pages: $pages", pages.last() >= 1)
    }

    @Test fun reflowFallsBackToThePageWhenThereIsNoLayout() {
        show(layout = PdfLayout.REFLOW) // FakeSource has no layouts
        assertTrue("rendered: ${book.rendered}", 0 in book.rendered)
    }

    @Test fun pinchInReflowStepsTheTextSizeOnce() {
        show(source = reflowable, layout = PdfLayout.REFLOW, reflowSize = 2)
        compose.onNodeWithTag(PDF_REFLOW_TAG).performTouchInput {
            pinch(center - Offset(40f, 0f), center - Offset(200f, 0f), center + Offset(40f, 0f), center + Offset(200f, 0f))
        }
        compose.waitForIdle()
        assertEquals(listOf(3), sizes)
    }

    @Test fun switchingToReflowKeepsThePage() {
        var layout by mutableStateOf(PdfLayout.VERTICAL)
        compose.setContent {
            CompositionLocalProvider(LocalKeyEventDispatcher provides dispatcher) {
                PdfReaderView(reflowable, layout, cropMargins = false, initialPage = 5, onPageChanged = { pages += it }, onCenterTap = {})
            }
        }
        compose.waitForIdle()
        layout = PdfLayout.REFLOW
        compose.waitForIdle()
        assertEquals(5, pages.last())
    }
```

- Add the imports `androidx.compose.ui.geometry.Offset`, `androidx.compose.ui.test.pinch`, `dev.kent.windbook.core.model.LayoutBlock`, `dev.kent.windbook.core.model.PageLayout`, `dev.kent.windbook.core.model.PageRect`, `dev.kent.windbook.core.model.ReflowSizes` and `dev.kent.windbook.reader.pdf.PDF_REFLOW_TAG`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :core:data:testDebugUnitTest --tests 'dev.kent.windbook.core.data.DisplayPreferencesRepositoryTest'`
Expected: compilation FAILS (`Unresolved reference: REFLOW`).

- [ ] **Step 3: Layout enum**

In `PdfDisplay.kt`: `enum class PdfLayout { VERTICAL, PAGED, REFLOW }`.

- [ ] **Step 4: Reflow view**

In `PdfReaderView.kt`:

- Add `const val PDF_REFLOW_TAG = "pdf-reflow"` next to the other tags, and `private val ReflowPadding = 16.dp` next to `PagedSurround`.
- Add the parameters at the end of `PdfReaderView`: `reflowSize: Int = ReflowSizes.DEFAULT, onReflowSizeChange: (Int) -> Unit = {},`
- Add the branch to `when (layout)`:

```kotlin
        PdfLayout.REFLOW -> ReflowPages(
            source, currentPage, cropFor, resolveCrop, report, onCenterTap, reflowSize, onReflowSizeChange, modifier,
        )
```

- In `VerticalPages`, replace the `LaunchedEffect(listState) { snapshotFlow { … canScrollForward … } … }` block with `ReportPage(listState, onPageChanged)` and add:

```kotlin
/** Reports the page being read: the first visible one, or the last when the list can't scroll further. */
@Composable
private fun ReportPage(listState: LazyListState, onPageChanged: (Int) -> Unit) {
    LaunchedEffect(listState) {
        snapshotFlow {
            // Pages are shorter than the screen, so at the end the last page is never the first visible one.
            if (listState.canScrollForward) {
                listState.firstVisibleItemIndex
            } else {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: listState.firstVisibleItemIndex
            }
        }.distinctUntilChanged().collect(onPageChanged)
    }
}
```

- Add the reflow composables:

```kotlin
/** Reflow (spec §4.3): each page's words re-wrapped to the screen at the chosen text size. */
@Composable
private fun ReflowPages(
    source: PdfPageSource,
    startPage: Int,
    cropFor: (Int) -> CropBox,
    resolveCrop: suspend (Int) -> Unit,
    onPageChanged: (Int) -> Unit,
    onCenterTap: () -> Unit,
    reflowSize: Int,
    onReflowSizeChange: (Int) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = startPage)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val size by rememberUpdatedState(reflowSize)
    val changeSize by rememberUpdatedState(onReflowSizeChange)
    ReportPage(listState, onPageChanged)

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        val viewportW = constraints.maxWidth
        val viewportH = constraints.maxHeight
        val contentW = (viewportW - 2 * with(density) { ReflowPadding.roundToPx() }).coerceAtLeast(1)
        val lineHeightPx = with(density) { ReflowSizes.lineHeightDp(reflowSize).dp.toPx() }

        PageTurnKeys { forward ->
            scope.launch { listState.animateScrollBy(viewportH * 0.9f * if (forward) 1 else -1) }
            true
        }

        // The next page is analysed and rendered ahead so scrolling lands on a ready page (spec §4.3).
        LaunchedEffect(listState, source, contentW, lineHeightPx) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { first ->
                val next = first + 1
                if (next >= source.pageCount) return@collectLatest
                try {
                    val crop = cropFor(next)
                    val layout = source.pageLayout(next, crop) ?: return@collectLatest
                    val scale = ReflowLayouter.layout(layout, contentW.toFloat(), lineHeightPx).scale
                    source.render(next, crop, ReflowLayouter.sourceWidth(scale, viewportW))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A broken page shows the page image when it scrolls in.
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .testTag(PDF_REFLOW_TAG)
                .pinchStep { bigger ->
                    val next = (size + if (bigger) 1 else -1).coerceIn(0, ReflowSizes.LAST)
                    if (next != size) changeSize(next)
                }
                .pointerInput(Unit) { detectTapGestures(onTap = { onCenterTap() }) },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(ReflowPadding),
                verticalArrangement = Arrangement.spacedBy(ReflowPadding),
            ) {
                items(count = source.pageCount, key = { it }) { page ->
                    LaunchedEffect(page, resolveCrop) {
                        source.measure(page)
                        resolveCrop(page)
                    }
                    val crop = cropFor(page)
                    var layout by remember(page, crop) { mutableStateOf<PageLayout?>(null) }
                    LaunchedEffect(page, crop) {
                        layout = try {
                            source.pageLayout(page, crop)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val reflow = remember(layout, contentW, lineHeightPx) {
                        layout?.let { ReflowLayouter.layout(it, contentW.toFloat(), lineHeightPx) }
                    }
                    val width = with(density) { contentW.toDp() }
                    if (reflow == null) {
                        // Not analysed yet (or can't be): the page as in vertical mode.
                        val heightPx = renderedHeight(source.pageSize(page), crop, contentW)
                        PageBitmap(source, page, crop, renderWidthPx = contentW, modifier = Modifier.width(width).height(with(density) { heightPx.toDp() }))
                    } else {
                        ReflowedPage(
                            source = source,
                            page = page,
                            crop = crop,
                            reflow = reflow,
                            renderWidthPx = ReflowLayouter.sourceWidth(reflow.scale, viewportW),
                            modifier = Modifier.width(width).height(with(density) { reflow.height.toDp() }),
                        )
                    }
                }
            }
        }
    }
}

/** Draws a page's reflowed pieces from one bitmap of its crop box. */
@Composable
private fun ReflowedPage(source: PdfPageSource, page: Int, crop: CropBox, reflow: ReflowPage, renderWidthPx: Int, modifier: Modifier) {
    var image by remember(page, crop, renderWidthPx) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(page, crop, renderWidthPx) {
        image = try {
            source.render(page, crop, renderWidthPx).asImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    Canvas(modifier.background(Color.White)) {
        val img = image ?: return@Canvas
        for (piece in reflow.pieces) {
            val sx = (piece.src.left * img.width).roundToInt().coerceIn(0, img.width - 1)
            val sy = (piece.src.top * img.height).roundToInt().coerceIn(0, img.height - 1)
            val sw = ((piece.src.right * img.width).roundToInt() - sx).coerceIn(1, img.width - sx)
            val sh = ((piece.src.bottom * img.height).roundToInt() - sy).coerceIn(1, img.height - sy)
            drawImage(
                image = img,
                srcOffset = IntOffset(sx, sy),
                srcSize = IntSize(sw, sh),
                dstOffset = IntOffset(piece.x.roundToInt(), piece.y.roundToInt()),
                dstSize = IntSize(piece.width.roundToInt().coerceAtLeast(1), piece.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Medium,
            )
        }
    }
}

/** One text-size step per two-finger gesture: spread to enlarge, pinch to shrink. */
private fun Modifier.pinchStep(onStep: (bigger: Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = 1f
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                total *= event.calculateZoom()
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (total >= 1.25f) onStep(true) else if (total <= 0.8f) onStep(false)
    }
}
```

- Add the imports `androidx.compose.foundation.Canvas`, `androidx.compose.foundation.layout.PaddingValues`, `kotlinx.coroutines.flow.collectLatest` (already present), `androidx.compose.ui.graphics.FilterQuality`, `androidx.compose.ui.unit.IntSize`, `dev.kent.windbook.core.model.PageLayout`, `dev.kent.windbook.core.model.ReflowLayouter`, `dev.kent.windbook.core.model.ReflowPage` and `dev.kent.windbook.core.model.ReflowSizes`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :core:data:testDebugUnitTest :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.*'`
Expected: PASS, including the 4 new view tests and the codec test.

- [ ] **Step 6: Commit**

```bash
git add core/model/src core/data/src app/src/main/kotlin/dev/kent/windbook/reader/pdf app/src/test/kotlin/dev/kent/windbook/reader/pdf
git commit -m "feat(app): Reflow view re-wraps PDF words to the screen, pinch steps the text size"
```

---

### Task 9: Reflow in the View menu, text size, wiring

**Files:**
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderScreen.kt` (`ReaderOverlay`, `ViewMenu`, `ReaderRoute`)
- Modify: `app/src/main/kotlin/dev/kent/windbook/reader/ReaderViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-vi/strings.xml`
- Test: `app/src/test/kotlin/dev/kent/windbook/reader/ReaderOverlayTest.kt`, `app/src/test/kotlin/dev/kent/windbook/reader/ReaderViewModelPdfTest.kt`

**Interfaces:**
- Consumes: `PdfLayout.REFLOW`, `PdfReaderView(reflowSize, onReflowSizeChange)` (Task 8). `EffectiveDisplay.reflowSize`, `ReflowSizes` (Task 2).
- Produces: `ReaderOverlay(…, onReflowSizeChange: (Int) -> Unit = {})`, `ReaderViewModel.setReflowSize(level: Int)`.

- [ ] **Step 1: Strings**

`values/strings.xml`:

```xml
    <string name="layout_reflow">Reflow</string>
    <string name="text_smaller">A−</string>
    <string name="text_larger">A+</string>
    <string name="cd_text_smaller">Smaller text</string>
    <string name="cd_text_larger">Larger text</string>
```

`values-vi/strings.xml`:

```xml
    <string name="layout_reflow">Dàn lại chữ</string>
    <string name="text_smaller">A−</string>
    <string name="text_larger">A+</string>
    <string name="cd_text_smaller">Chữ nhỏ hơn</string>
    <string name="cd_text_larger">Chữ lớn hơn</string>
```

- [ ] **Step 2: Write the failing tests**

Append inside `ReaderOverlayTest`:

```kotlin
    @Test fun reflowIsAThirdViewChoice() {
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
        compose.onNodeWithText("Reflow").performClick()
        assertEquals(listOf(PdfLayout.REFLOW), layouts)
    }

    @Test fun textSizeButtonsAreHiddenOutsideReflow() {
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.VERTICAL, cropMargins = true),
            )
        }
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithContentDescription("Larger text").assertDoesNotExist()
    }

    @Test fun largerTextStepsTheReflowSize() {
        val sizes = mutableListOf<Int>()
        compose.setContent {
            ReaderOverlay(
                visible = true, title = "Doc", chapterTitle = null, progression = 0.1,
                canMarkFinished = false, onMarkFinished = {}, onBack = {},
                pdfDisplay = EffectiveDisplay(PdfLayout.REFLOW, cropMargins = true, reflowSize = 2),
                onReflowSizeChange = { sizes += it },
            )
        }
        compose.onNodeWithContentDescription("View").performClick()
        compose.onNodeWithContentDescription("Larger text").performClick()
        compose.onNodeWithContentDescription("Smaller text").performClick()
        assertEquals(listOf(3, 1), sizes)
    }
```

Append inside `ReaderViewModelPdfTest`:

```kotlin
    @Test fun reflowSizeIsSavedForTheBook() = runTest {
        val model = vm(PageSize(400f, 600f))
        model.pdfDisplay.filterNotNull().first()
        model.setReflowSize(5)
        assertEquals(5, prefs.observe("a").first { it.reflowSize != null }.reflowSize)
        assertEquals(5, model.pdfDisplay.filterNotNull().first { it.reflowSize == 5 }.reflowSize)
    }
```

- [ ] **Step 3: Run them to verify they fail**

Run: `./gradlew :app:testDebugUnitTest --tests 'dev.kent.windbook.reader.ReaderOverlayTest' --tests 'dev.kent.windbook.reader.ReaderViewModelPdfTest'`
Expected: compilation FAILS (`No parameter with name 'onReflowSizeChange'`, `Unresolved reference: setReflowSize`).

- [ ] **Step 4: Implement**

In `ReaderViewModel.kt`, after `setPdfZoom`:

```kotlin
    fun setReflowSize(level: Int) = updatePrefs { it.copy(reflowSize = level.coerceIn(0, ReflowSizes.LAST)) }
```

with the import `dev.kent.windbook.core.model.ReflowSizes`.

In `ReaderScreen.kt`:
- Add `onReflowSizeChange: (Int) -> Unit = {},` as the last `ReaderOverlay` parameter, and pass it on: `ViewMenu(pdfDisplay, onPdfLayoutChange, onCropMarginsChange, onReflowSizeChange)`.
- Replace `ViewMenu` with:

```kotlin
@Composable
private fun ViewMenu(
    display: EffectiveDisplay,
    onLayoutChange: (PdfLayout) -> Unit,
    onCropChange: (Boolean) -> Unit,
    onReflowSizeChange: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(WindBookIcons.ViewMode, contentDescription = stringResource(R.string.cd_view_mode))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(
                PdfLayout.VERTICAL to R.string.layout_vertical,
                PdfLayout.PAGED to R.string.layout_paged,
                PdfLayout.REFLOW to R.string.layout_reflow,
            ).forEach { (layout, label) ->
                DropdownMenuItem(
                    text = { Text(stringResource(label)) },
                    leadingIcon = { RadioButton(selected = display.layout == layout, onClick = null) },
                    onClick = {
                        open = false
                        if (display.layout != layout) onLayoutChange(layout)
                    },
                )
            }
            if (display.layout == PdfLayout.REFLOW) {
                // The menu stays open so the size can be stepped several times.
                val smaller = stringResource(R.string.cd_text_smaller)
                val larger = stringResource(R.string.cd_text_larger)
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = { onReflowSizeChange(display.reflowSize - 1) },
                        enabled = display.reflowSize > 0,
                        modifier = Modifier.semantics { contentDescription = smaller },
                    ) { Text(stringResource(R.string.text_smaller)) }
                    TextButton(
                        onClick = { onReflowSizeChange(display.reflowSize + 1) },
                        enabled = display.reflowSize < ReflowSizes.LAST,
                        modifier = Modifier.semantics { contentDescription = larger },
                    ) { Text(stringResource(R.string.text_larger)) }
                }
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

- Add the imports `androidx.compose.ui.semantics.contentDescription` and `dev.kent.windbook.core.model.ReflowSizes` (`Row`, `padding`, `TextButton`, `Alignment` are already imported).
- In `ReaderRoute`, add to the `PdfReaderView(…)` call `reflowSize = display.reflowSize, onReflowSizeChange = viewModel::setReflowSize,`, and to the `ReaderOverlay(…)` call `onReflowSizeChange = viewModel::setReflowSize,`.

- [ ] **Step 5: Run the tests, lint and build**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Lint reports no new warnings (still 0 errors, 8 pre-existing warnings).

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "feat(app): Reflow in the View menu with A−/A+ text size, remembered per book"
```

---

### Task 10: Device verification, measurements and docs

**Files:**
- Modify: `docs/testing/m1-manual-checklist.md` (new "PDF comfort" section)
- Modify: `docs/research/2026-10-15-library-analysis.md` (results)
- Modify: `docs/superpowers/plans/2026-10-13-windbook-roadmap.md` (new row)

**Interfaces:**
- Consumes: the whole feature.
- Produces: measured numbers for spec criteria 1–5. Screenshots go to the scratchpad and are not committed (they contain book text).

- [ ] **Step 1: Device tests**

Run: `./gradlew :reader:connectedDebugAndroidTest`
Expected: all PASS (14 existing + 2 import + 2 layout).

- [ ] **Step 2: Criterion 1 – import the scanned textbook**

```bash
./gradlew :app:installDebug
adb shell ls /sdcard/Download/   # pdf_scan_vi.pdf must be there; push it from Library/ via metadata.json if not
```

In the app, delete the scanned textbook from the library (⋯ → Delete). Start `adb shell screenrecord --time-limit 15 /sdcard/import.mp4` in the background, then add the book again with the + button. While it imports, run `adb shell dumpsys meminfo dev.kent.windbook | grep "TOTAL PSS"` twice: once before tapping Select and once right after. Pull the video (`adb pull /sdcard/import.mp4 $SCRATCH/import.mp4`) and list its scene changes with `ffmpeg -hide_banner -i $SCRATCH/import.mp4 -vf "select='gt(scene,0.05)',showinfo" -f null - 2>&1 | grep -o "pts_time:[0-9.]*"`; the import time is from the change at the Select tap to the change when "1 book(s) added" appears (check the frames with `ffmpeg -ss <t> -i $SCRATCH/import.mp4 -frames:v 1 $SCRATCH/f.png`). Pass: ≤ 1.5 s, PSS growth < 100 MB, and the title "Cẩm Nang Tư Duy Học Tập Và Nghiên Cứu" and cover appear.

- [ ] **Step 3: Criterion 2 – zoom memory**

Open the LaTeX book, double-tap to zoom, swipe sideways, go back, reopen: same zoom and position. Press volume down three times while zoomed: right, right edge, back left and down. Open a book never zoomed: 1×, no sideways panning.

- [ ] **Step 4: Criteria 3–5 – Reflow**

For the Vietnamese scan, the calibre Vietnamese PDF and the LaTeX book:
- View → Reflow.
- Screenshot a body page at the default size (`adb exec-out screencap -p > $SCRATCH/reflow-<name>.png`).
- Step A+ twice, then screenshot again.

Measure:
- **Criterion 3.** Body text height in the calibre reflow shot vs the same page with Crop margins off in Vertical. For each shot, take the ink profile of a 300 px tall band of body text: `magick $SCRATCH/<shot>.png -alpha off -crop 1080x300+0+900 +repage -colorspace gray -threshold 50% -negate -scale 1x300\! -depth 8 txt:- | awk 'NR>1{split($1,a,","); v=$2; gsub(/[()]/,"",v); print a[2], v}'`. Rows with ink form one run per text line. The median run-to-run distance is the line pitch. Pass: reflow pitch ÷ uncropped pitch ≥ 1.8.
- **Diacritics.** Zoom into the scan shot: no tone mark on its own row or attached to the wrong line.
- **Criterion 4.** Add a temporary `Log.d("WBDBG", …)` timing around `pageLayout` in `PdfPages` (do not commit it). Scroll 10 scan pages in Reflow, read the per-page times (pass: ≤ 300 ms), then switch Vertical → Reflow and time the first reflowed page from the video (pass: ≤ 1.5 s). Remove the log.
- **Criterion 5.** Note the page, switch Reflow → Vertical → Reflow, and confirm the page is the same.
- **Memory.** `dumpsys meminfo` TOTAL PSS after scrolling 30 pages in Reflow at the largest size. Pass: < 400 MB.

- [ ] **Step 5: Docs**

Append to `docs/testing/m1-manual-checklist.md`:

```markdown
## PDF comfort (2026-10-16)

| # | Step | Expected | Emulator | Neo 3 GT |
|---|---|---|---|---|
| C1 | Import the scanned textbook (2654×4329 pt pages) | ≤ 1.5 s, PSS +< 100 MB, title and cover shown | | |
| C2 | Zoom a portrait PDF, swipe sideways, reopen | Same zoom and position; never-zoomed books open at 1× | | |
| C3 | Volume down ×3 while zoomed | Right, right edge, back to the left edge and down | | |
| C4 | View → Reflow on the scanned textbook | Words re-wrapped, no sideways panning, tone marks on their letters | | |
| C5 | Reflow A+ / A− and pinch | Text size steps once per tap or pinch; remembered after reopening | | |
| C6 | Reflow → Vertical → Reflow | Same page | | |
| C7 | Reflow, 30 pages at the largest size | TOTAL PSS < 400 MB | | |
```

Fill the Emulator column with the results from Steps 2–4. Add a "PDF comfort (2026-10-16)" table to the research note with the measured numbers: import time and PSS, reflow text-height ratio, per-page analysis time, first-page time and PSS. Add to the roadmap table after the PDF display row:

```markdown
| PDF comfort | `2026-10-16-windbook-pdf-comfort.md` | PDF display | Light PDF import (no full-size Readium cover), per-book zoom memory with reading-order volume keys, image reflow (lines/words/figures, A−/A+) | Done (branch `pdf-comfort`) |
```

- [ ] **Step 6: Full CI check and commit**

Run: `./gradlew :core:model:test :core:data:testDebugUnitTest :reader:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

```bash
git add docs
git commit -m "docs: PDF comfort checks, measurements and roadmap entry"
```
