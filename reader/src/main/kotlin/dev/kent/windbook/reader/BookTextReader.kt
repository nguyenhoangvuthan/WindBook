package dev.kent.windbook.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.resource.InMemoryResource
import org.readium.r2.shared.util.mediatype.MediaType

data class BookTextSection(val location:String,val text:String,val locatorJson:String="")
fun learningChunks(location:String,text:String,locatorJson:String=""):List<BookTextSection> {
    require(text.length<=2_097_152)
    return text.chunked(12000).mapIndexed {index,part ->BookTextSection("$location#${index*12000}",part,locatorJson)}
}

/** Explicit preparation only. It reuses the verified open session, never scans the library. */
@OptIn(ExperimentalReadiumApi::class)
class BookTextReader(private val session:ReaderSession) {
    fun sections():Flow<BookTextSection> = flow {
        var total=0L;var count=0
        suspend fun section(value:BookTextSection) {
            currentCoroutineContext().ensureActive();total+=value.text.length;count++
            require(total<=24_000_000 && count<=2048)
            emit(value)
        }
        if(session is PdfSession) {
            require(session.pages.textSelectionAvailable)
            for(page in 0 until session.pageCount) {
                val text=session.pages.pageText(page) ?: throw ReaderException("Page text unavailable")
                for(chunk in learningChunks("page:${page+1}",text,session.locatorJsonFor(page))) section(chunk)
            }
        } else {
            val pub=(session as? ReadiumReaderSession)?.publication ?: throw ReaderException("Publication unavailable")
            require(session.format==dev.kent.windbook.core.model.BookFormat.EPUB)
            for((index,link) in pub.readingOrder.withIndex()) {
                currentCoroutineContext().ensureActive()
                val locator=pub.locatorFromLink(link) ?: throw ReaderException("Resource location unavailable")
                val resource=pub.get(link) ?: throw ReaderException("Resource unavailable")
                val bytes=try {
                    require((resource.length().getOrNull() ?: 0)<=2_097_152)
                    val data=resource.read(0L..2_097_152L).getOrNull() ?: throw ReaderException("Resource unavailable")
                    require(data.size<=2_097_152);data
                } finally {resource.close()}
                val memory=InMemoryResource(bytes)
                try {
                    val iterator=HtmlResourceContentIterator.Factory().create(pub.manifest,pub,index,memory,MediaType.XHTML,locator)
                        ?: throw ReaderException("Unsupported resource")
                    val text=StringBuilder()
                    while(iterator.hasNext()) {
                        currentCoroutineContext().ensureActive()
                        val item=iterator.next()
                        if(item is Content.TextualElement) {
                            require(text.length+item.text.orEmpty().length+1<=2_097_152)
                            text.append(item.text.orEmpty()).append('\n')
                        }
                    }
                    for(chunk in learningChunks("section:${index+1}",text.toString(),locator.toJsonString())) section(chunk)
                } finally {memory.close()}
            }
        }
    }.flowOn(Dispatchers.Default)
}
