package dev.kent.windbook.reader

import android.os.Looper
import dev.kent.windbook.reader.source.SourceLease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val readerCleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** AutoCloseable callers may be UI callbacks. Rendering joins and disk cleanup must not block them. */
internal fun closeReaderResources(close: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) readerCleanup.launch { close() }
    else close()
}

/** Owns the source until construction succeeds, then the reader session owns its lifetime. */
internal suspend fun openOwnedReaderSession(
    source: SourceLease,
    create: suspend () -> ReaderSession,
): Result<ReaderSession> {
    var session: ReaderSession? = null
    try {
        return withContext(Dispatchers.IO) {
            session = create()
            currentCoroutineContext().ensureActive()
            Result.success(checkNotNull(session))
        }
    } catch (e: Throwable) {
        // Construction transfers source ownership exactly once. A session may close
        // asynchronously on Main; its teardown alone releases the snapshot afterward.
        val owner = session
        if (owner != null) owner.close() else source.close()
        if (e is CancellationException) throw e
        if (e is Error) throw e
        return Result.failure(e)
    }
}
