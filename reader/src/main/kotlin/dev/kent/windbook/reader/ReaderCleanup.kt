package dev.kent.windbook.reader

import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val readerCleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** AutoCloseable callers may be UI callbacks. Rendering joins and disk cleanup must not block them. */
internal fun closeReaderResources(close: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) readerCleanup.launch { close() }
    else close()
}
