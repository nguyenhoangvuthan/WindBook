package dev.kent.windbook.core.data

import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import org.json.JSONArray
import org.json.JSONObject

class PdfContentsRepository(private val db: WindBookDatabase) {
    suspend fun find(bookId: String,sha: String): PdfContents? {
        val book=db.libraryDao().findById(bookId) ?: return null
        if(book.sha256!=sha) return null
        val row=db.pdfContentsDao().find(bookId,sha,1) ?: return null
        return try { decode(row) } catch(_: Exception) { null }
    }
    suspend fun save(bookId: String,sha: String,value: PdfContents): Boolean = db.withWriteTransaction { saveLocked(bookId,sha,value) }
    internal suspend fun saveLocked(bookId: String,sha: String,value: PdfContents): Boolean {
        val book=db.libraryDao().findById(bookId) ?: return false
        if(book.sha256!=sha || book.format!="PDF") return false
        val json=encode(value.entries)
        if(json.toByteArray(Charsets.UTF_8).size>1024*1024) return false
        db.pdfContentsDao().upsert(PdfContentsEntity(bookId,sha,1,value.status.name,json))
        return true
    }
    private fun encode(nodes: List<ContentsEntry>): String = JSONArray().apply {nodes.forEach {node ->
        put(JSONObject().put("id",node.id).put("title",node.title).put("page",node.page ?: JSONObject.NULL).put("children",JSONArray(encode(node.children))))
    }}.toString()
    private fun decode(row: PdfContentsEntity): PdfContents {
        require(row.contentsJson.length<=1024*1024)
        var count=0
        fun nodes(array: JSONArray,depth: Int): List<ContentsEntry> {
            require(depth<=32)
            return (0 until array.length()).map {i ->
                require(++count<=10000)
                val n=array.getJSONObject(i)
                ContentsEntry(n.getString("id"),n.getString("title").also {require(it.length<=1024)},
                    if(n.isNull("page")) null else n.getInt("page"),nodes(n.getJSONArray("children"),depth+1))
            }
        }
        return PdfContents(ContentsStatus.valueOf(row.status),nodes(JSONArray(row.contentsJson),0))
    }
}
