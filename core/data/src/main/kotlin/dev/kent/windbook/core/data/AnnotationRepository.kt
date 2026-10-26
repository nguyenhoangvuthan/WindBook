package dev.kent.windbook.core.data

import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.db.*
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** App-private annotations; source documents are never rewritten. */
class AnnotationRepository(private val db: WindBookDatabase, private val clock: () -> Long = System::currentTimeMillis) {
    fun observe(bookId: String? = null): Flow<List<PdfTextAnnotation>> = db.annotationDao().observe(bookId).map { rows -> rows.map { it.annotation.toModel(it.bookTitle) } }
    suspend fun find(id: String): PdfTextAnnotation? = db.annotationDao().find(id)?.toModel()
    suspend fun save(bookId: String, fingerprint: String, anchor: PdfTextAnchor, color: AnnotationColor, body: String?): String = db.withWriteTransaction {
        require(anchor.isValid())
        val book=requireNotNull(db.libraryDao().findById(bookId))
        require(book.format=="PDF" && fingerprint.isNotBlank() && book.sha256==fingerprint)
        val clean=cleanBody(body)
        val id=UUID.randomUUID().toString(); val now=clock()
        db.annotationDao().upsert(AnnotationEntity(id,bookId,fingerprint,encode(anchor),anchor.quote,color.name,clean,now,now))
        id
    }
    suspend fun update(id: String, color: AnnotationColor, body: String?) = db.withWriteTransaction {
        val current=requireNotNull(db.annotationDao().find(id))
        db.annotationDao().upsert(current.copy(color=color.name,body=cleanBody(body),updatedAt=clock()))
    }
    suspend fun delete(id: String) = db.annotationDao().delete(id)
    private fun cleanBody(body: String?): String? = body?.trim()?.also { require(it.isNotEmpty() && it.length<=65_536) }
    private fun AnnotationEntity.toModel(title: String="") = PdfTextAnnotation(id,bookId,fingerprint,decode(anchorJson),quote,
        runCatching { AnnotationColor.valueOf(color) }.getOrDefault(AnnotationColor.GOLD),body,createdAt,updatedAt,title)

    private fun encode(anchor: PdfTextAnchor): String = JSONObject().apply {
        put("version",anchor.version); put("page",anchor.page); put("width",anchor.width); put("height",anchor.height); put("quote",anchor.quote)
        put("rects",JSONArray().apply { anchor.rects.forEach { put(JSONArray(listOf(it.left,it.top,it.right,it.bottom))) } })
    }.toString()
    private fun decode(json: String): PdfTextAnchor? = try {
        if(json.length>131_072) null else {
            val root=JSONObject(json); val rects=root.getJSONArray("rects")
            if(rects.length()>PdfTextAnchor.MAX_RECTS) null else PdfTextAnchor(root.getInt("version"),root.getInt("page"),
                root.getDouble("width").toFloat(),root.getDouble("height").toFloat(),
                (0 until rects.length()).map { i -> val r=rects.getJSONArray(i); PdfTextRect(r.getDouble(0).toFloat(),r.getDouble(1).toFloat(),r.getDouble(2).toFloat(),r.getDouble(3).toFloat()) },
                root.getString("quote")).takeIf { it.isValid() }
        }
    } catch (_: Exception) { null }
}
