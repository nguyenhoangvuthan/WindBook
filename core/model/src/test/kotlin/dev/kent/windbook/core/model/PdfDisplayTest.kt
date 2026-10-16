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
}
