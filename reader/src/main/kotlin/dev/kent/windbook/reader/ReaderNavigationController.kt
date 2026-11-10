package dev.kent.windbook.reader

import dev.kent.windbook.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Layout
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.ExperimentalReadiumApi

data class ReaderSearchHit(val locatorJson: String,val snippet: String)

/** Publication-only adapter; PDFs always navigate through PdfSession. */
@OptIn(ExperimentalReadiumApi::class)
class ReaderNavigationController(private val session: ReaderSession) {
    private val publication get() = (session as? ReadiumReaderSession)?.publication
    val reflowable: Boolean get() = publication?.metadata?.layout != Layout.FIXED && publication!=null
    suspend fun contents(): List<ContentsEntry> = withContext(Dispatchers.Default) {
        val pub=publication ?: return@withContext emptyList()
        var count=0
        fun nodes(links: List<Link>,parent: String,depth: Int): List<ContentsEntry> {
            require(depth<=32)
            return links.mapIndexed {i,link ->
                require(++count<=10000)
                val id="$parent/$i"
                ContentsEntry(id,link.title.orEmpty(),children=nodes(link.children,id,depth+1),locatorJson=pub.locatorFromLink(link)?.toJsonString())
            }
        }
        val toc=pub.tableOfContents
        // Normalized TXT sections are resources, not invented chapters.
        nodes(if(toc.isEmpty() && session.format==BookFormat.TXT) pub.readingOrder else toc,"",0)
    }
    suspend fun position(fraction: Double): String? {
        if(!fraction.isFinite()) return null
        val pub=publication ?: return null
        val positions=pub.positions()
        val index=seekPage(fraction,positions.size) ?: return null
        return positions[index].toJsonString()
    }
    suspend fun search(query: String): ReaderSearchCursor? {
        val pub=publication ?: return null
        return BoundedReaderSearchCursor(query) {index ->
            pub.readingOrder.getOrNull(index)?.let {link ->
                val locator=pub.locatorFromLink(link) ?: throw ReaderException("Search destination unavailable")
                val resource=pub.get(link) ?: throw ReaderException("Search resource unavailable")
                SearchResource(resource,locator,pub.manifest,pub,index)
            }
        }
    }
}

interface ReaderSearchCursor : AutoCloseable {
    val exhausted: Boolean
    suspend fun next(): List<ReaderSearchHit>
}

/** Saved PDF parsing tolerates legacy data; deliberate jumps must reject invalid destinations. */
fun pdfNavigationPage(json: String,count: Int): Int? {
    val locator=json.toLocatorOrNull() ?: return null
    if(locator.href.toString()!="publication.pdf") return null
    val position=locator.locations.position?.takeIf {it in 1..count} ?: return null
    if(locator.locations.fragments.any {it.startsWith("page=") && it!="page=$position"}) return null
    return position-1
}

interface ReaderNavigationAccess {
    suspend fun position(session: ReaderSession,fraction: Double): String?
    suspend fun search(session: ReaderSession,query: String): ReaderSearchCursor?
}
object DefaultReaderNavigationAccess : ReaderNavigationAccess {
    override suspend fun position(session: ReaderSession,fraction: Double)=ReaderNavigationController(session).position(fraction)
    override suspend fun search(session: ReaderSession,query: String)=ReaderNavigationController(session).search(query)
}
