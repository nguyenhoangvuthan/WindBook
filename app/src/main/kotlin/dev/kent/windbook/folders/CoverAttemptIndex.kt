package dev.kent.windbook.folders

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import dev.kent.windbook.core.data.folders.FolderCoverCandidate
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/** Disposable run-local index. Bounded disk/page cache, independent of the durable library DB. */
internal class CoverAttemptIndex private constructor(private val file: File, private val db: SQLiteDatabase) : AutoCloseable {
    fun matches(candidate: FolderCoverCandidate): Boolean = try {
        db.query("attempts", arrayOf("signature"), "source_id = ?", arrayOf(candidate.source.id), null, null, null).use {
            it.moveToFirst() && it.getBlob(0).contentEquals(signature(candidate))
        }
    } catch (_: SQLiteException) { false }

    fun remember(candidate: FolderCoverCandidate) {
        try {
            db.insertWithOnConflict("attempts", null, ContentValues().apply {
                put("source_id", candidate.source.id)
                put("signature", signature(candidate))
            }, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (_: SQLiteException) { /* Cache exhaustion may repeat work, never skip verification. */ }
    }

    override fun close() {
        try { db.close() } finally { SQLiteDatabase.deleteDatabase(file) }
    }

    private fun signature(candidate: FolderCoverCandidate): ByteArray {
        val source = candidate.source
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                listOf(candidate.runId, source.id, source.bookId, source.kind, source.localPath,
                    source.uri, source.authority, source.documentId, source.size?.toString(), source.mtime?.toString(),
                    source.verifiedSha256, source.availability, source.txtEncodingOverride,
                    candidate.format.name, candidate.bookSha256, candidate.coverPath, candidate.relativePath).forEach { field ->
                    if (field == null) output.writeInt(-1)
                    else field.toByteArray(Charsets.UTF_8).let { output.writeInt(it.size); output.write(it) }
                }
            }
            buffer.toByteArray()
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    companion object {
        fun open(file: File): CoverAttemptIndex? {
            var opened: SQLiteDatabase? = null
            return try {
                file.parentFile?.mkdirs()
                // An interrupted process cannot lend proof to a new run.
                SQLiteDatabase.deleteDatabase(file)
                val db = SQLiteDatabase.openOrCreateDatabase(file, null).also { opened = it }
                db.rawQuery("PRAGMA journal_mode=MEMORY", null).use { it.moveToFirst() }
                db.execSQL("PRAGMA synchronous=OFF")
                db.execSQL("PRAGMA cache_size=-256")
                db.maximumSize = 8L * 1024 * 1024
                db.execSQL("CREATE TABLE attempts (source_id TEXT PRIMARY KEY NOT NULL, signature BLOB NOT NULL)")
                CoverAttemptIndex(file, db)
            } catch (_: SQLiteException) {
                try { opened?.close() } finally { SQLiteDatabase.deleteDatabase(file) }
                null
            }
        }
    }
}
