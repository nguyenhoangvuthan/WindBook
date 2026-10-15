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
