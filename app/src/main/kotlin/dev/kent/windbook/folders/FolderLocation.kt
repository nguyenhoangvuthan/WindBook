package dev.kent.windbook.folders

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri

/** A hint is never permission. Only an exact held tree or a successful Android picker grants access. */
data class FolderLocation(val treeUri: Uri, val initialUri: Uri) {
    companion object {
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"

        // Parse user-supplied aliases only. All access still goes through a real SAF tree grant.
        @SuppressLint("SdCardPath")
        fun resolve(input: String): FolderLocation? {
            val value = input.trim().trimEnd('/')
            val tree = if (value.startsWith("content://")) {
                val uri = value.toUri()
                if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank() ||
                    uri.pathSegments.size != 2 || !DocumentsContract.isTreeUri(uri) ||
                    uri.query != null || uri.fragment != null || DocumentsContract.getTreeDocumentId(uri).isBlank()) return null
                uri
            } else {
                val (volume, relative) = when {
                    value == "/sdcard" || value.startsWith("/sdcard/") -> "primary" to value.removePrefix("/sdcard").trimStart('/')
                    value == "/storage/emulated/0" || value.startsWith("/storage/emulated/0/") ->
                        "primary" to value.removePrefix("/storage/emulated/0").trimStart('/')
                    else -> {
                        val match = Regex("^/storage/([0-9a-fA-F]{4}-[0-9a-fA-F]{4})(?:/(.*))?$").matchEntire(value) ?: return null
                        match.groupValues[1] to match.groupValues[2]
                    }
                }
                val segments = relative.split('/').filter { it.isNotEmpty() }
                if (segments.any { it == "." || it == ".." || '\u0000' in it }) return null
                DocumentsContract.buildTreeDocumentUri(EXTERNAL_STORAGE, "$volume:${segments.joinToString("/")}")
            }
            return FolderLocation(tree, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)))
        }
    }
}
