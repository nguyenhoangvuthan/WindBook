package dev.kent.windbook.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import dev.kent.windbook.core.data.db.BookPreferencesDao
import dev.kent.windbook.core.model.*
import kotlinx.coroutines.flow.map
import org.json.JSONObject

internal object TypographyCodec {
    fun encode(value: ReaderTypography): String = merge(null,value)
    fun merge(previous: String?,value: ReaderTypography): String = (try {JSONObject(previous ?: "{}")} catch(_: Exception) {JSONObject()}).apply {
        put("font",value.font?.name);put("size",value.size);put("lineHeight",value.lineHeight)
        put("margin",value.margin);put("colors",value.colors?.name)
    }.toString()
    fun decode(json: String?): ReaderTypography = try {
        val o=JSONObject(json ?: "{}")
        ReaderTypography(font=o.optString("font").let {s->ReaderFont.entries.find {it.name==s}},
            size=if(o.has("size")) o.optInt("size") else null,
            lineHeight=if(o.has("lineHeight")) o.optDouble("lineHeight") else null,
            margin=if(o.has("margin")) o.optInt("margin") else null,
            colors=o.optString("colors").let {s->ReaderColors.entries.find {it.name==s}}).normalized()
    } catch(_: Exception) { ReaderTypography() }
}
class BookTypographyRepository(private val dao: BookPreferencesDao) {
    fun observe(bookId: String) = dao.observeJson(bookId).map { json -> TypographyCodec.decode(root(json).optJSONObject("readerTypography")?.toString()) }
    suspend fun set(bookId: String,value: ReaderTypography) = update(bookId) {value}
    suspend fun update(bookId: String,change: (ReaderTypography)->ReaderTypography) = dao.transform(bookId,System.currentTimeMillis()) {json ->
        val all=root(json)
        val previous=all.optJSONObject("readerTypography")?.toString()
        all.put("readerTypography",JSONObject(TypographyCodec.merge(previous,change(TypographyCodec.decode(previous)).normalized()))).toString()
    }
    private fun root(json: String?): JSONObject = try {JSONObject(json ?: "{}")} catch(_: Exception) {JSONObject()}
}
class ReaderPreferencesRepository(private val store: DataStore<Preferences>) {
    private val key=stringPreferencesKey("reader_typography")
    val typography=store.data.map {TypographyCodec.decode(it[key])}
    suspend fun set(value: ReaderTypography) = update {value}
    suspend fun update(change: (ReaderTypography)->ReaderTypography) {store.edit {prefs ->
        val previous=prefs[key]
        prefs[key]=TypographyCodec.merge(previous,change(TypographyCodec.decode(previous)).normalized())
    }}
}
