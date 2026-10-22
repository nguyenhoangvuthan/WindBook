package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.folders.FolderRepository
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey

class BookSourcesRepository(
    private val db: WindBookDatabase,
    private val folders: FolderRepository,
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

    suspend fun disconnectFolder(folderId: String) = folders.disconnectFolder(folderId)
}
