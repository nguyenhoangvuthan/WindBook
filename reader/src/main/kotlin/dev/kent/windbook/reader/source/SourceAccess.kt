package dev.kent.windbook.reader.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.CancellationSignal
import dev.kent.windbook.core.model.BookSource
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Acquires external bytes once. Provider URI reopening cannot preserve a verified revision. */
class SourceAccess(
    context: Context,
    workspace: SessionWorkspace? = null,
) {
    private val configuredWorkspace = workspace
    // Engine construction can happen on main; orphan deletion starts inside acquire's IO context.
    val workspace: SessionWorkspace by lazy {
        configuredWorkspace ?: SessionWorkspace(File(context.cacheDir, "reader-sessions"))
    }
    private val resolver = context.contentResolver

    /** Call from app startup even if no book opens. Repeated calls retain every active lease. */
    suspend fun initialize() = withContext(Dispatchers.IO) { workspace.cleanupOrphans() }

    suspend fun acquire(source: BookSource): SourceLease {
        var acquired: SourceLease? = null
        try {
            return withContext(Dispatchers.IO) {
                when (source) {
                    is BookSource.Owned -> {
                        val file = File(source.path)
                        require(file.isFile) { "Missing source file" }
                        if (file.length() > workspace.limits.sourceBytes) throw SourceTooLargeException()
                        SourceLease(file, null, workspace.limits.bufferBytes)
                    }
                    is BookSource.External -> {
                        val session = workspace.create()
                        val signal = CancellationSignal()
                        val descriptor = AtomicReference<ParcelFileDescriptor?>()
                        // Closing a blocked pipe is necessary: cancellation checks between reads alone
                        // cannot interrupt a provider that stops producing bytes.
                        val cancellationCloser = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                            try { awaitCancellation() }
                            finally { signal.cancel(); descriptor.get()?.close() }
                        }
                        try {
                            // One provider open only. Pipes, unknown lengths and seekable descriptors use
                            // the same bounded snapshot; no retries after denied or corrupt content.
                            val fd = resolver.openFileDescriptor(Uri.parse(source.uri), "r", signal)
                                ?: throw java.io.FileNotFoundException("Source descriptor unavailable")
                            descriptor.set(fd)
                            currentCoroutineContext().ensureActive()
                            val declaredSize = fd.statSize.takeIf { it >= 0 }
                            val file = session.stage(ParcelFileDescriptor.AutoCloseInputStream(fd), declaredSize)
                            SourceLease(file, session, workspace.limits.bufferBytes).also { acquired = it }
                        } catch (e: Throwable) {
                            session.close()
                            throw e
                        } finally {
                            withContext(NonCancellable) { cancellationCloser.cancelAndJoin() }
                            descriptor.get()?.close()
                        }
                    }
                }.also { acquired = it; currentCoroutineContext().ensureActive() }
            }
        } catch (e: Throwable) {
            acquired?.close()
            currentCoroutineContext().ensureActive()
            throw e
        }
    }
}

/** Independent file opens have independent offsets; dup() alone shares an open-file offset. */
class SourceLease internal constructor(
    val file: File,
    private val workspace: SessionLease?,
    private val bufferBytes: Int,
) : AutoCloseable {
    private val closed = AtomicBoolean()
    fun openStream(): InputStream {
        check(!closed.get()) { "Source lease is closed" }
        return file.inputStream()
    }
    fun openDescriptor(): ParcelFileDescriptor {
        check(!closed.get()) { "Source lease is closed" }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    suspend fun fingerprint(): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        openStream().use { input ->
            val buffer = ByteArray(bufferBytes)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) dev.kent.windbook.reader.closeReaderResources { workspace?.close() }
    }
}
