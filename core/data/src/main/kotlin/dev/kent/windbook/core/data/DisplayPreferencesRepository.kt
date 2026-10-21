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

/** JSON so later milestones can add per-book fields without another migration; unknown keys are kept on write. */
internal object BookPreferencesCodec {
    private const val LAYOUT = "pdfLayout"
    private const val CROP = "cropMargins"
    private const val ZOOM = "pdfZoom"
    private const val ZOOM_X = "pdfZoomX"

    fun decode(json: String?): BookDisplayPrefs {
        val obj = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return BookDisplayPrefs()
        val layout = obj.optString(LAYOUT).takeIf { it.isNotEmpty() && !obj.isNull(LAYOUT) }
            ?.let {
                // Reflow was removed; reopen old choices in vertical mode, including landscape books.
                if (it == "REFLOW") PdfLayout.VERTICAL else runCatching { PdfLayout.valueOf(it) }.getOrNull()
            }
        val crop = if (obj.present(CROP)) obj.optBoolean(CROP) else null
        return BookDisplayPrefs(
            pdfLayout = layout,
            cropMargins = crop,
            zoom = obj.floatOrNull(ZOOM),
            zoomX = obj.floatOrNull(ZOOM_X),
        )
    }

    fun encode(previous: String?, prefs: BookDisplayPrefs): String {
        val obj = previous?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        obj.put(LAYOUT, prefs.pdfLayout?.name ?: JSONObject.NULL)
        obj.put(CROP, prefs.cropMargins ?: JSONObject.NULL)
        obj.put(ZOOM, prefs.zoom?.toDouble() ?: JSONObject.NULL)
        obj.put(ZOOM_X, prefs.zoomX?.toDouble() ?: JSONObject.NULL)
        return obj.toString()
    }

    private fun JSONObject.present(key: String) = has(key) && !isNull(key)

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (present(key)) optDouble(key).takeIf { it.isFinite() }?.toFloat() else null
}
