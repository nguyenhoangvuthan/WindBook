package dev.kent.windbook.core.data

import androidx.room3.withWriteTransaction
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey

class BookSourcesRepository(
    private val db: WindBookDatabase,
    private val releaseGrant: suspend (treeUri: String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun sources(bookId: String): List<BookSource> = db.sourceDao().forBook(bookId).map { source ->
        when (source.kind) {
            "OWNED" -> BookSource.Owned(requireNotNull(source.localPath))
            "EXTERNAL" -> BookSource.External(
                requireNotNull(source.uri),
                DocumentKey(requireNotNull(source.authority), requireNotNull(source.documentId)),
            )
            else -> error("Unknown source kind: ${source.kind}")
        }
    }

    suspend fun hideExternalBook(bookId: String) = db.libraryDao().hideExternal(bookId, clock())

    suspend fun restoreExternalBook(bookId: String) = db.libraryDao().restoreExternal(bookId, clock())

    suspend fun disconnectFolder(folderId: String) {
        val unusedGrant = db.withWriteTransaction {
            val folder = db.folderDao().find(folderId) ?: return@withWriteTransaction null
            db.folderDao().disconnect(folderId, clock())
            db.sourceDao().disconnectOrphans(folderId)
            folder.treeUri.takeUnless { db.sourceDao().connectedSourceUsesGrant(it) }
        }
        // Keep disconnected rows/memberships: a shared canonical URI may still need this grant.
        // FolderGrants supplies the actual read-only grant release; no source file is touched.
        if (unusedGrant != null) releaseGrant(unusedGrant)
    }
}
