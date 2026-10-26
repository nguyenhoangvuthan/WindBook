package dev.kent.windbook.core.model

import org.junit.Assert.*
import org.junit.Test

class PdfTextAnnotationTest {
    @Test fun highDensityTopEdgePopupClearsHandlesAndStaysBelowStatusBar() {
        // 3px/dp; handle extends24dp below the last line, then12dp clear space.
        assertEquals(248,pdfSelectionPopupY(80f,140f,90,72,2250,3f))
        assertEquals(356,pdfSelectionPopupY(80f,140f,90,72,2250,3f,handleScale=2.5f))
        // With room above, keep12dp between text and popup.
        assertEquals(334,pdfSelectionPopupY(460f,540f,90,72,2250,3f))
        // A bottom-edge selection uses room above instead of clipping under navigation.
        assertEquals(2074,pdfSelectionPopupY(2200f,2240f,90,72,2300,3f))
    }

    @Test fun lowerPassageRevealScrollsInsideTallPageAndClampsShortPages() {
        val anchor=PdfTextAnchor(page=0,width=400f,height=2000f,rects=listOf(PdfTextRect(.1f,.75f,.8f,.8f)),quote="Lower passage")
        assertEquals(1350,pdfRevealScrollOffset(anchor,2000,600))
        assertEquals(0,pdfRevealScrollOffset(anchor,400,600))
    }

    @Test fun croppedDisplayMapsToFullPageAndBackAtZoomedWidth() {
        val geometry = PdfPageGeometry(PageSize(400f, 600f), CropBox(.1f,.2f,.9f,.8f), 640f,720f)
        val page = geometry.toPage(PdfPoint(320f,360f))!!
        assertEquals(200f, page.x, .001f); assertEquals(300f, page.y, .001f)
        val display = geometry.toDisplay(PdfPoint(200f,300f))
        assertEquals(320f, display.x, .001f); assertEquals(360f, display.y, .001f)
        assertNull(geometry.toPage(PdfPoint(-1f,10f)))
    }
    @Test fun invalidAnchorsAndUnboundedTextCannotBecomeAnnotations() {
        val valid = PdfTextAnchor(1, 0, 400f,600f,listOf(PdfTextRect(.1f,.2f,.4f,.3f)),"Tiếng Việt")
        assertTrue(valid.isValid())
        assertFalse(valid.copy(page=-1).isValid())
        assertFalse(valid.copy(version=2).isValid())
        assertFalse(valid.copy(width=Float.NaN).isValid())
        assertFalse(valid.copy(rects=emptyList()).isValid())
        assertFalse(valid.copy(quote=" ").isValid())
        assertFalse(valid.copy(rects=listOf(PdfTextRect(0f,0f,1.1f,1f))).isValid())
    }
}
