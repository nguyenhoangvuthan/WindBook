package dev.kent.windbook.core.data

import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.db.*
import java.security.MessageDigest
import org.json.JSONObject

class BookmarkRepository(private val db: WindBookDatabase, private val clock: () -> Long=System::currentTimeMillis) {
    fun observe(bookId: String,sha: String,encoding: String?) = db.bookmarkDao().observe(bookId,sha,encoding)
    suspend fun save(bookId: String,sha: String,locator: String,progression: Double,label: String,encoding: String?): String? = db.withWriteTransaction {
        val book=db.libraryDao().findById(bookId) ?: return@withWriteTransaction null
        if(book.sha256!=sha || sha.isBlank() || !progression.isFinite() || locator.length>65536) return@withWriteTransaction null
        val anchor=try { JSONObject(locator).takeIf {it.optString("href").isNotBlank()} } catch(_: Exception) {null}
            ?: return@withWriteTransaction null
        // Text and title are presentation, not anchor identity.
        anchor.remove("text");anchor.remove("title")
        val bytes=listOf(bookId,sha,encoding.orEmpty(),anchor.toString()).joinToString("\u0000").toByteArray()
        val id=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {"%02x".format(it)}
        val old=db.bookmarkDao().find(id)
        if(old?.deletedAt==null && old!=null) return@withWriteTransaction id
        val now=clock()
        db.bookmarkDao().upsert(BookmarkEntity(id,bookId,sha,locator,progression.coerceIn(0.0,1.0),label.take(512),encoding,old?.createdAt ?: now,now))
        id
    }
    suspend fun rename(id: String,label: String) { require(label.isNotBlank() && label.length<=512);db.bookmarkDao().rename(id,label.trim(),clock()) }
    suspend fun remove(id: String) = db.bookmarkDao().remove(id,clock())
}
