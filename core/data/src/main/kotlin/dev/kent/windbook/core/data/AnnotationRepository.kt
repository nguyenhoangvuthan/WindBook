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
    private fun AnnotationEntity.toModel(title: String="") = PdfTextAnnotation(id,bookId,fingerprint,
        if(kind=="PDF_TEXT") decode(anchorJson) else null,quote,
        runCatching { AnnotationColor.valueOf(color) }.getOrDefault(AnnotationColor.GOLD),body,createdAt,updatedAt,title,
        kind,if(kind=="EPUB_TEXT") decodeEpub(anchorJson)?.takeIf {it.quote==quote} else null)

    suspend fun saveEpub(bookId: String, fingerprint: String, anchor: EpubTextAnchor, color: AnnotationColor, body: String?): String = db.withWriteTransaction {
        require(anchor.isValid() && validLocator(anchor))
        val book=requireNotNull(db.libraryDao().findById(bookId))
        require(book.format=="EPUB" && fingerprint.isNotBlank() && book.sha256==fingerprint)
        val now=clock(); val id=UUID.randomUUID().toString()
        val json=JSONObject().apply {put("version",anchor.version);put("href",anchor.href);put("locator",anchor.locatorJson)
            put("quote",anchor.quote);put("before",anchor.before);put("after",anchor.after)}.toString()
        db.annotationDao().upsert(AnnotationEntity(id,bookId,fingerprint,json,anchor.quote,color.name,cleanBody(body),now,now,"EPUB_TEXT"))
        id
    }
    private fun validLocator(a: EpubTextAnchor): Boolean = try {
        val loc=JSONObject(a.locatorJson); val text=loc.getJSONObject("text")
        loc.getString("href")==a.href && text.getString("highlight")==a.quote &&
            text.optString("before")==a.before && text.optString("after")==a.after &&
            loc.optString("type") in setOf("application/xhtml+xml","text/html")
    } catch(_: Exception) {false}
    private fun decodeEpub(json: String): EpubTextAnchor? = try {
        if(json.length>65_536) null else JSONObject(json).let {r ->
            EpubTextAnchor(r.getInt("version"),r.getString("href"),r.getString("locator"),r.getString("quote"),r.optString("before"),r.optString("after"))
                .takeIf {it.isValid() && validLocator(it)}
        }
    } catch(_: Exception) {null}

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
