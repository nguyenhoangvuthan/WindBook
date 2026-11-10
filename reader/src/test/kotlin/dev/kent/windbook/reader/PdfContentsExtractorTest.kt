package dev.kent.windbook.reader

import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.metadata.pdfContentsFromLinks
import org.junit.Assert.*
import org.junit.Test
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class PdfContentsExtractorTest {
    private fun link(href: String,title: String,children: List<Link> = emptyList())=Link(href=Url(href)!!,title=title,children=children)
    @Test fun oneBasedPdfPagesAndNestedLabelsSurviveConversion() {
        val result=pdfContentsFromLinks(listOf(link("publication.pdf#page=1","First",listOf(link("publication.pdf#page=3","Tiếng Việt")))),3)
        assertEquals(1,result.entries.single().page)
        assertEquals(3,result.entries.single().children.single().page)
    }
    @Test fun groupingAndExternalOrBadDestinationsNeverGuessAPage() {
        val result=pdfContentsFromLinks(listOf(link("publication.pdf#page=null","Group",listOf(
            link("https://example.com/#page=2","External"),link("publication.pdf#page=99","Bad"),link("publication.pdf#page=2","Good")))),3)
        assertNull(result.entries.single().page)
        assertEquals(listOf(null,null,2),result.entries.single().children.map {it.page})
    }
}
