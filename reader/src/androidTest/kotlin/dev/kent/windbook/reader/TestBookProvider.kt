package dev.kent.windbook.reader

import android.database.Cursor
import android.database.MatrixCursor
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Synthetic read-only documents. State is addressed through provider calls across test processes. */
class TestBookProvider : DocumentsProvider() {
    private val attempts = ConcurrentHashMap<String, AtomicInteger>()
    private val active = AtomicInteger()
    private val stalled = CountDownLatch(1)
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = when (method) {
        "stats" -> Bundle().apply {
            putInt("active", active.get())
            putInt("attempts", attempts[arg]?.get() ?: 0)
        }
        "release" -> Bundle().also { stalled.countDown() }
        else -> super.call(method, arg, extras) ?: Bundle()
    }
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(arrayOf("root_id"))
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor =
        MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID))
    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE)).apply {
            addRow(arrayOf<Any>(documentId, documentId, source(documentId).length()))
        }
    override fun getDocumentType(documentId: String) = if (documentId.endsWith("epub")) "application/epub+zip" else "application/pdf"
    private fun source(id: String) = File(context!!.cacheDir, id.removePrefix("pipe-").removePrefix("denied-"))
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        check(mode == "r")
        attempts.computeIfAbsent(documentId) { AtomicInteger() }.incrementAndGet()
        if (documentId.startsWith("denied")) throw SecurityException("Synthetic denial")
        if (documentId.startsWith("stalled-")) {
            val pipe = ParcelFileDescriptor.createPipe()
            active.incrementAndGet()
            Thread {
                try { stalled.await(10, TimeUnit.SECONDS) }
                finally { pipe[1].close(); active.decrementAndGet() }
            }.start()
            return pipe[0]
        }
        val file = source(documentId)
        if (!file.exists()) throw FileNotFoundException(documentId)
        if (documentId.startsWith("pipe-")) {
            val pipe = ParcelFileDescriptor.createPipe()
            active.incrementAndGet()
            Thread {
                try { ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { output -> file.inputStream().use { it.copyTo(output) } } }
                catch (_: java.io.IOException) { /* Reader cancellation closes the pipe. */ }
                finally { active.decrementAndGet() }
            }.start()
            return pipe[0]
        }
        active.incrementAndGet()
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY, Handler(Looper.getMainLooper())) {
                active.decrementAndGet()
            }
        } catch (e: Throwable) { active.decrementAndGet(); throw e }
    }
}
