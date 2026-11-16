package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfDisplayTest {
    private val portrait = PageSize(504f, 657f)
    private val slide = PageSize(720f, 540f)

    @Test fun portraitBooksDefaultToPageByPage() =
        assertEquals(PdfLayout.PAGED, defaultPdfLayout(List(5) { portrait }))

    @Test fun slidesArePaged() = assertEquals(PdfLayout.PAGED, defaultPdfLayout(List(5) { slide }))

    @Test fun mixedPageSizesDefaultToPageByPage() =
        assertEquals(PdfLayout.PAGED, defaultPdfLayout(listOf(slide, portrait, portrait, portrait, portrait)))

    @Test fun unknownPageSizesDefaultToPageByPage() = assertEquals(PdfLayout.PAGED, defaultPdfLayout(emptyList()))

    @Test fun marginsAreCroppedByDefaultInEveryLayout() {
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, true), BookDisplayPrefs().resolve(listOf(portrait)))
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, true), BookDisplayPrefs().resolve(listOf(slide)))
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, true), BookDisplayPrefs(pdfLayout = PdfLayout.PAGED).resolve(listOf(portrait)))
        assertEquals(EffectiveDisplay(PdfLayout.VERTICAL, true), BookDisplayPrefs(pdfLayout = PdfLayout.VERTICAL).resolve(listOf(portrait)))
        // Fit page and the View menu store an explicit false, which must keep winning.
        assertEquals(EffectiveDisplay(PdfLayout.PAGED, false), BookDisplayPrefs(PdfLayout.PAGED, cropMargins = false).resolve(listOf(portrait)))
    }

    @Test fun explicitChoicesWin() =
        assertEquals(
            EffectiveDisplay(PdfLayout.VERTICAL, true),
            BookDisplayPrefs(PdfLayout.VERTICAL, cropMargins = true).resolve(listOf(portrait)),
        )

    @Test fun savedZoomAndHorizontalPositionAreKept() =
        assertEquals(
            EffectiveDisplay(PdfLayout.PAGED, true, zoom = 2.5f, zoomX = 0.4f),
            BookDisplayPrefs(zoom = 2.5f, zoomX = 0.4f).resolve(listOf(portrait)),
        )

    @Test fun neverZoomedOpensAtFitWidth() {
        val display = BookDisplayPrefs().resolve(listOf(portrait))
        assertEquals(1f, display.zoom)
        assertEquals(0f, display.zoomX)
    }

    @Test fun outOfRangeSavedValuesAreClamped() =
        assertEquals(
            EffectiveDisplay(PdfLayout.PAGED, true, zoom = MAX_PDF_ZOOM, zoomX = 0f),
            BookDisplayPrefs(zoom = 9f, zoomX = -1f).resolve(listOf(portrait)),
        )

}
