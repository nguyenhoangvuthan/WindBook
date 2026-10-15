package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.BookPreferencesDao
import dev.kent.windbook.core.data.db.BookPreferencesEntity
import dev.kent.windbook.core.model.BookDisplayPrefs
import dev.kent.windbook.core.model.PdfLayout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class DisplayPreferencesRepository(
    private val dao: BookPreferencesDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(bookId: String): Flow<BookDisplayPrefs> = dao.observeJson(bookId).map(BookPreferencesCodec::decode)

    suspend fun set(bookId: String, prefs: BookDisplayPrefs) {
        val json = BookPreferencesCodec.encode(dao.json(bookId), prefs)
        dao.upsert(BookPreferencesEntity(bookId, json, clock()))
    }
}

/** JSON so M2 can add per-book fields without another migration; unknown keys are kept on write. */
internal object BookPreferencesCodec {
    private const val LAYOUT = "pdfLayout"
    private const val CROP = "cropMargins"

    fun decode(json: String?): BookDisplayPrefs {
        val obj = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return BookDisplayPrefs()
        val layout = obj.optString(LAYOUT).takeIf { it.isNotEmpty() && !obj.isNull(LAYOUT) }
            ?.let { runCatching { PdfLayout.valueOf(it) }.getOrNull() }
        val crop = if (obj.has(CROP) && !obj.isNull(CROP)) obj.optBoolean(CROP) else null
        return BookDisplayPrefs(layout, crop)
    }

    fun encode(previous: String?, prefs: BookDisplayPrefs): String {
        val obj = previous?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        obj.put(LAYOUT, prefs.pdfLayout?.name ?: JSONObject.NULL)
        obj.put(CROP, prefs.cropMargins ?: JSONObject.NULL)
        return obj.toString()
    }
}
