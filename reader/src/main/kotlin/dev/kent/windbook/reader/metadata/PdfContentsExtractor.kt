package dev.kent.windbook.reader.metadata

import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.ReadiumServices
import java.io.File
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link

fun pdfContentsFromLinks(links: List<Link>,pageCount: Int,documentHref: String="publication.pdf"): PdfContents {
    var count=0
    fun map(nodes: List<Link>,parent: String,depth: Int): List<ContentsEntry> {
        require(depth<=32)
        return nodes.mapIndexed {i,link ->
            require(++count<=10000)
            val id="$parent/$i"
            val href=link.href.toString()
            val base=href.substringBefore('#')
            val fragment=runCatching {URI(href).rawFragment}.getOrNull()
            val page=if(base.isEmpty() || base==documentHref) fragment?.takeIf {it.matches(Regex("page=[0-9]+"))}?.removePrefix("page=")?.toIntOrNull() else null
            ContentsEntry(id,link.title.orEmpty(),page,map(link.children,id,depth+1))
        }
    }
    return try {normalizePdfContents(map(links,"",0),pageCount)} catch(_: IllegalArgumentException) {PdfContents(ContentsStatus.UNAVAILABLE)}
}

class PdfContentsExtractor(private val services: ReadiumServices,
    private val work: MetadataWorkCoordinator=MetadataWorkCoordinator.shared) {
    suspend fun read(file: File,pageCount: Int): PdfContents = work.run {
        withContext(Dispatchers.IO) {
            val publication=services.openPublication(file).getOrThrow()
            try {pdfContentsFromLinks(publication.tableOfContents,pageCount,publication.readingOrder.firstOrNull()?.href.toString().substringBefore('#'))}
            finally {publication.close()}
        }
    }
}
