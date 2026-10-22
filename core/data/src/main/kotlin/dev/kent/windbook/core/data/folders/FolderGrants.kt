package dev.kent.windbook.core.data.folders

import android.content.ContentResolver
import android.content.Intent

class FolderGrants(private val resolver: ContentResolver) {
    fun persistRead(treeUri: String, returnedFlags: Int) {
        val uri = requireTreeUri(treeUri)
        if (returnedFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            throw SecurityException("Folder did not grant read access")
        }
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun persistedReadTrees(): Set<String> = resolver.persistedUriPermissions
        .filter { it.isReadPermission && android.provider.DocumentsContract.isTreeUri(it.uri) }
        .map { it.uri.toString() }.toSet()

    fun hasRead(treeUri: String): Boolean {
        val uri = requireTreeUri(treeUri)
        return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun releaseRead(treeUri: String) {
        val uri = requireTreeUri(treeUri)
        if (resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }) {
            resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
