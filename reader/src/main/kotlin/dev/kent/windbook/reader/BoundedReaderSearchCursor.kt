package dev.kent.windbook.reader

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.publication.services.content.iterators.HtmlResourceContentIterator
import org.readium.r2.shared.util.resource.InMemoryResource
import org.readium.r2.shared.util.resource.Resource

class ReaderSearchLimitException : Exception("Search resource exceeds bounded parsing size")
internal data class SearchResource(val resource: Resource,val locator: Locator,
    val manifest: org.readium.r2.shared.publication.Manifest,val services: org.readium.r2.shared.publication.PublicationServicesHolder,val index: Int=0)

/** A bounded resource parser and incremental match producer, never the toolkit's all-matches iterator. */
@OptIn(ExperimentalReadiumApi::class)
internal class BoundedReaderSearchCursor(private val query: String,
    private val open: suspend (Int)->SearchResource?): ReaderSearchCursor {
    private val closed=AtomicBoolean()
    private var resourceIndex=0
    private var iterator: Content.Iterator?=null
    private var memory: Resource?=null
    private var text=""
    private var anchor: Locator?=null
    private var offset=0
    override var exhausted=query.isEmpty()
        private set
    var scannedCharacters=0L
        private set

    private suspend fun loadResource(): Boolean {
        memory?.close();memory=null;iterator=null
        val item=open(resourceIndex++) ?: run {exhausted=true;return false}
        val bytes=try {
            val length=item.resource.length().getOrNull()
            if(length!=null && length>MAX_BYTES) throw ReaderSearchLimitException()
            val data=item.resource.read(0L..MAX_BYTES).getOrNull() ?: throw ReaderException("Search resource unavailable")
            if(data.size>MAX_BYTES) throw ReaderSearchLimitException()
            data
        } finally {item.resource.close()}
        currentCoroutineContext().ensureActive()
        val bounded=InMemoryResource(bytes).also {memory=it}
        iterator=HtmlResourceContentIterator.Factory().create(item.manifest,item.services,item.index,bounded,org.readium.r2.shared.util.mediatype.MediaType.XHTML,item.locator)
            ?: throw ReaderException("Unsupported search content")
        return true
    }
    private suspend fun loadText(): Boolean {
        while(!exhausted) {
            currentCoroutineContext().ensureActive()
            val active=iterator
            if(active==null || !active.hasNext()) {if(!loadResource()) return false;continue}
            val element=active.next()
            val content=(element as? Content.TextualElement)?.text
            if(!content.isNullOrEmpty()) {
                text=content.replace(Regex("\\s+")," ").trim();anchor=element.locator;offset=0
                return true
            }
        }
        return false
    }
    override suspend fun next(): List<ReaderSearchHit> {
        check(!closed.get())
        val page=ArrayList<ReaderSearchHit>(50)
        while(page.size<50 && !exhausted) {
            currentCoroutineContext().ensureActive()
            if(offset>=text.length && !loadText()) break
            val at=text.indexOf(query,offset,ignoreCase=true)
            if(at<0) {scannedCharacters+=text.length-offset;offset=text.length;continue}
            val end=at+query.length
            scannedCharacters+=end-offset
            offset=end
            val before=text.substring((at-48).coerceAtLeast(0),at)
            val match=text.substring(at,end)
            val after=text.substring(end,(end+80).coerceAtMost(text.length))
            val base=requireNotNull(anchor)
            val locator=base.copy(locations=base.locations.copy(otherLocations=base.locations.otherLocations+("windbookTextOffset" to at)),
                text=Locator.Text(before=before,highlight=match,after=after))
            page+=ReaderSearchHit(locator.toJsonString(),before+match+after)
        }
        return page
    }
    override fun close() {
        if(closed.compareAndSet(false,true)) {memory?.close();memory=null;iterator=null;text="";anchor=null;exhausted=true}
    }
    companion object {private const val MAX_BYTES=2L*1024*1024}
}
