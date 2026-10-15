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
import androidx.compose.ui.test.doubleClick
import dev.kent.windbook.core.model.CropBox
import dev.kent.windbook.core.model.CropPlan
import dev.kent.windbook.core.model.PageSize
import dev.kent.windbook.core.model.PdfLayout
import dev.kent.windbook.input.KeyEventDispatcher
import dev.kent.windbook.input.LocalKeyEventDispatcher
import dev.kent.windbook.reader.pdf.PDF_PAGED_TAG
import dev.kent.windbook.reader.pdf.PDF_VERTICAL_TAG
import dev.kent.windbook.reader.pdf.PdfReaderView
import java.lang.ref.WeakReference
import kotlinx.coroutines.delay
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

    private fun show(source: PdfPageSource = book, layout: PdfLayout, initialPage: Int = 0, cropMargins: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalKeyEventDispatcher provides dispatcher) {
                PdfReaderView(
                    source = source, layout = layout, cropMargins = cropMargins, initialPage = initialPage,
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

    /** Review #3: a page fitted to the width is shorter than the screen, so the last page is never the first visible one. */
    @Test fun scrollingToTheEndReportsTheLastPage() {
        show(layout = PdfLayout.VERTICAL)
        repeat(30) { compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { swipeUp() } }
        compose.waitForIdle()
        assertEquals(19, pages.last())
    }

    @Test fun reopeningOnTheLastPageKeepsTheLastPage() {
        show(layout = PdfLayout.VERTICAL, initialPage = 19)
        assertEquals(19, pages.last())
    }

    /** Review #1: pages already on screen when the (slow) book crop plan lands still get their own, wider box. */
    @Test fun visiblePagesGetTheirOwnCropOnceThePlanArrives() {
        val source = object : PdfPageSource by FakeSource(List(10) { PageSize(400f, 600f) }) {
            val asked = mutableListOf<Int>()
            val crops = mutableListOf<Pair<Int, CropBox>>()
            override suspend fun cropPlan(): CropPlan {
                delay(300)
                return CropBox(0.2f, 0.2f, 0.8f, 0.8f).let { CropPlan(it, it) }
            }
            // A full-bleed cover: its own content fills the page.
            override suspend fun pageCrop(page: Int, plan: CropPlan): CropBox = CropBox.FULL.also { asked += page }
            override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap {
                if (region == null) crops += page to crop
                return Bitmap.createBitmap(region?.width ?: widthPx, region?.height ?: renderedHeight(pageSize(page), crop, widthPx), Bitmap.Config.ARGB_8888)
            }
        }
        show(source = source, layout = PdfLayout.VERTICAL, cropMargins = true)
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertTrue("pageCrop asked for: ${source.asked}", 0 in source.asked)
        assertEquals(CropBox.FULL, source.crops.last { it.first == 0 }.second)
    }

    /** Review #2: zoomed detail tiles of pages scrolled away must not stay in memory. */
    @Test fun zoomTilesOfPagesScrolledAwayAreReleased() {
        val tiles = mutableListOf<WeakReference<Bitmap>>()
        val source = object : PdfPageSource by FakeSource(List(40) { PageSize(400f, 600f) }) {
            override suspend fun render(page: Int, crop: CropBox, widthPx: Int, region: PixelRegion?): Bitmap =
                Bitmap.createBitmap(region?.width ?: widthPx, region?.height ?: renderedHeight(pageSize(page), crop, widthPx), Bitmap.Config.ARGB_8888)
                    .also { if (region != null) tiles += WeakReference(it) }
        }
        show(source = source, layout = PdfLayout.VERTICAL)
        compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { doubleClick(center) }
        compose.mainClock.advanceTimeBy(1_000)
        repeat(25) {
            compose.onNodeWithTag(PDF_VERTICAL_TAG).performTouchInput { swipeUp() }
            compose.mainClock.advanceTimeBy(1_000)
            compose.waitForIdle()
        }
        repeat(3) { System.gc() }
        val alive = tiles.count { it.get() != null }
        assertTrue("tiles rendered: ${tiles.size}, still alive: $alive", tiles.size > 5 && alive <= 4)
    }
}
