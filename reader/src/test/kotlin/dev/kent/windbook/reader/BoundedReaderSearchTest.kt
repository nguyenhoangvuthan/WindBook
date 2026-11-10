package dev.kent.windbook.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.readium.r2.shared.util.resource.InMemoryResource

@RunWith(RobolectricTestRunner::class)
class BoundedReaderSearchTest {
    private val manifest=org.readium.r2.shared.publication.Manifest(metadata=org.readium.r2.shared.publication.Metadata(localizedTitle=org.readium.r2.shared.publication.LocalizedString("Search")))
    private val services=object: org.readium.r2.shared.publication.PublicationServicesHolder {
        override fun close()=Unit
        override fun <T: org.readium.r2.shared.publication.Publication.Service> findService(serviceType: kotlin.reflect.KClass<T>): T?=null
        override fun <T: org.readium.r2.shared.publication.Publication.Service> findServices(serviceType: kotlin.reflect.KClass<T>): List<T> = emptyList()
    }
    private val locator="""{"href":"chapter.xhtml","type":"application/xhtml+xml"}""".toLocatorOrNull()!!
    @Test fun denseResourceDoesNotMaterializeAllMatchesBeforeFirstPage()=runTest {
        val bytes=("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p id=\"dense\">"+"a ".repeat(200000)+"</p></body></html>").toByteArray()
        val cursor=BoundedReaderSearchCursor("a") {index ->if(index==0) SearchResource(InMemoryResource(bytes),locator,manifest,services) else null}
        try {
            assertEquals(50,cursor.next().size)
            assertTrue("Producer must stop matching once the first page is full",cursor.scannedCharacters<=100)
            assertEquals(50,cursor.next().size)
            assertTrue(cursor.scannedCharacters<=200)
        } finally {cursor.close()}
    }
    @Test fun oversizedResourceIsRejectedBeforeHtmlParsingOrMatchAllocation()=runTest {
        val cursor=BoundedReaderSearchCursor("a") {index ->if(index==0) SearchResource(InMemoryResource(ByteArray(2*1024*1024+1) {97}),locator,manifest,services) else null}
        try {
            try {cursor.next();fail("Oversized input must report an explicit limit")} catch(_: ReaderSearchLimitException) {}
            assertEquals(0L,cursor.scannedCharacters)
        } finally {cursor.close()}
    }
}
