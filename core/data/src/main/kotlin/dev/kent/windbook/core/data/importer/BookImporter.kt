package dev.kent.windbook.core.data.importer

import dev.kent.windbook.core.data.db.BookEntity
import dev.kent.windbook.core.data.db.LibraryDao
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.data.files.FileTooLargeException
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.FileSignature
import dev.kent.windbook.core.model.FormatSniffer
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.core.model.ReadingStatus
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookImporter(
    private val dao: LibraryDao,
    private val files: BookFileStore,
    private val metadata: MetadataExtractor,
    private val zipGuard: ZipGuard = ZipGuard(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val maxBytes: Long = 500L * 1024 * 1024,
) {
    suspend fun import(source: ImportSource): ImportResult = withContext(Dispatchers.IO) {
        val copied = try {
            source.open().use { files.copyToTemp(it, maxBytes) }
        } catch (e: FileTooLargeException) {
            return@withContext ImportResult.Failed(ImportError.TOO_LARGE)
        } catch (e: IOException) {
            return@withContext ImportResult.Failed(ioError())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // e.g. SecurityException when a provider revokes access mid-import.
            return@withContext ImportResult.Failed(ImportError.READ_FAILED)
        }

        try {
            val format = when (FormatSniffer.signature(files.readHeader(copied.file))) {
                FileSignature.PDF -> BookFormat.PDF
                FileSignature.ZIP -> {
                    zipGuard.inspectEpub(copied.file)?.let { return@withContext ImportResult.Failed(it) }
                    BookFormat.EPUB
                }
                FileSignature.UNKNOWN -> return@withContext ImportResult.Failed(ImportError.UNSUPPORTED_FORMAT)
            }

            val existing = dao.findBySha(copied.sha256)
            if (existing != null && existing.deletedAt == null) {
                return@withContext ImportResult.Duplicate(existing.id)
            }

            val id = existing?.id ?: newId()
            val bookFile = files.commit(copied.file, id, format)
            var coverPath: String? = null
            try {
                val meta = metadata.extract(bookFile, format).getOrElse {
                    bookFile.delete()
                    return@withContext ImportResult.Failed(ImportError.UNREADABLE)
                }
                coverPath = meta.coverWebp?.let { files.writeCover(id, it).absolutePath }
                val now = clock()
                val entity = BookEntity(
                    id = id,
                    title = meta.title?.takeIf { it.isNotBlank() }?.trim()
                        ?: source.displayName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                        ?: "Untitled",
                    author = meta.author?.takeIf { it.isNotBlank() }?.trim(),
                    format = format.name,
                    filePath = bookFile.absolutePath,
                    fileSize = copied.size,
                    sha256 = copied.sha256,
                    coverPath = coverPath,
                    sourceUri = source.sourceUri,
                    status = ReadingStatus.TO_READ.name,
                    addedAt = now,
                    lastOpenedAt = null,
                    updatedAt = now,
                    deletedAt = null,
                )
                if (existing != null) {
                    // Re-import of a tombstoned book: revive the same row.
                    dao.update(entity)
                    ImportResult.Imported(id)
                } else {
                    insertOrDuplicate(entity, bookFile, coverPath)
                }
            } catch (e: CancellationException) {
                bookFile.delete()
                files.delete(coverPath)
                throw e
            } catch (e: Exception) {
                // Disk full while writing the cover, database errors…: never leave orphaned files.
                bookFile.delete()
                files.delete(coverPath)
                ImportResult.Failed(if (e is IOException) ioError() else ImportError.UNREADABLE)
            }
        } finally {
            copied.file.delete() // no-op once committed (renamed away)
        }
    }

    /** Two concurrent imports of the same file race on the unique sha256 index. */
    private suspend fun insertOrDuplicate(entity: BookEntity, bookFile: File, coverPath: String?): ImportResult =
        try {
            dao.insert(entity)
            ImportResult.Imported(entity.id)
        } catch (e: Exception) {
            val winner = dao.findBySha(entity.sha256) ?: throw e
            bookFile.delete()
            files.delete(coverPath)
            ImportResult.Duplicate(winner.id)
        }

    private fun ioError(): ImportError =
        if (files.usableBytes() < LOW_SPACE_BYTES) ImportError.NO_SPACE else ImportError.READ_FAILED

    private companion object {
        const val LOW_SPACE_BYTES = 50L * 1024 * 1024
    }
}
