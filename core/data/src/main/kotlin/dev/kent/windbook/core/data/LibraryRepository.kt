package dev.kent.windbook.core.data

import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.LibraryDao
import dev.kent.windbook.core.data.db.ReadingProgressEntity
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.Book
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.core.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LibraryRepository(
    private val dao: LibraryDao,
    private val files: BookFileStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeLibrary(): Flow<List<LibraryItem>> =
        dao.observeLibrary().map { rows -> rows.map { LibraryItem(it.book.toDomain(), it.progression) } }

    suspend fun book(id: String): Book? = dao.findById(id)?.toDomain()

    suspend fun setStatus(id: String, status: ReadingStatus) = dao.updateStatus(id, status.name, clock())

    suspend fun markOpened(id: String) = dao.markOpened(id, clock())

    suspend fun delete(id: String) {
        val book = dao.findById(id) ?: return
        if (book.filePath == null) {
            dao.hideExternal(id, clock())
            return
        }
        dao.softDelete(id, clock())
        files.delete(book.filePath)
        files.delete(book.coverPath)
    }

    suspend fun savedLocator(id: String): String? = dao.progress(id)?.locatorJson

    suspend fun saveProgress(id: String, locatorJson: String, progression: Double) =
        dao.upsertProgress(ReadingProgressEntity(id, locatorJson, progression.coerceIn(0.0, 1.0), clock()))
}

internal fun BookEntity.toDomain() = Book(
    id = id,
    title = title,
    author = author,
    format = BookFormat.valueOf(format),
    filePath = filePath,
    fileSize = fileSize,
    sha256 = sha256,
    coverPath = coverPath,
    status = ReadingStatus.valueOf(status),
    addedAt = addedAt,
    lastOpenedAt = lastOpenedAt,
)
