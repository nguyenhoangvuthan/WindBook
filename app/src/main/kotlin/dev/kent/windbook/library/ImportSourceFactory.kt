package dev.kent.windbook.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.kent.windbook.core.data.importer.ImportSource
import java.io.FileNotFoundException
import java.io.InputStream

fun interface ImportSourceFactory {
    fun create(uri: Uri): ImportSource
}

class ContentResolverImportSourceFactory(private val resolver: ContentResolver) : ImportSourceFactory {
    override fun create(uri: Uri): ImportSource = object : ImportSource {
        override val displayName: String? by lazy { queryDisplayName(uri) }
        override val sourceUri: String = uri.toString()
        override fun open(): InputStream =
            resolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
}
