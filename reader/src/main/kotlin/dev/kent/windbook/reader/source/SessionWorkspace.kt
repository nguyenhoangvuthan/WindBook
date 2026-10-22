package dev.kent.windbook.reader.source

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Limits apply to observed bytes too; provider metadata never controls an allocation. */
data class SourceLimits(
    val sourceBytes: Long = 500L * 1024 * 1024,
    val scratchBytes: Long = 1024L * 1024 * 1024,
    val freeReserveBytes: Long = 64L * 1024 * 1024,
    val bufferBytes: Int = 64 * 1024,
) {
    init { require(sourceBytes > 0 && scratchBytes > 0 && freeReserveBytes >= 0 && bufferBytes > 0) }
}
class SourceTooLargeException : IOException("Source exceeds the session size limit")
class SourceSpaceException : IOException("Insufficient session workspace space")

/** Process-wide ownership per root: another manager must never clean a live session. */
class SessionWorkspace(
    private val root: File,
    internal val limits: SourceLimits = SourceLimits(),
    private val usableSpace: () -> Long = { root.usableSpace },
) {
    private class State {
        val active = mutableMapOf<File, Long>()
        val preparation = Mutex()
    }
    private val state = states.computeIfAbsent(root.canonicalPath) { State() }
    val activeLeaseCount: Int get() = synchronized(state) { state.active.size }
    val usedBytes: Long get() = synchronized(state) { state.active.values.sum() }

    init { cleanupOrphans() }

    fun cleanupOrphans() = synchronized(state) {
        if (!root.exists() && !root.mkdirs()) throw IOException("Cannot create session workspace")
        root.listFiles()?.filter { it !in state.active }?.forEach { it.deleteRecursively() }
        Unit
    }

    fun create(): SessionLease = synchronized(state) {
        val directory = File(root, UUID.randomUUID().toString())
        if (!directory.mkdir()) throw IOException("Cannot create reader session")
        state.active[directory] = 0
        SessionLease(directory, this)
    }

    internal suspend fun stage(lease: SessionLease, input: InputStream, declaredSize: Long?): File =
        input.use { stream ->
            withContext(Dispatchers.IO) {
                state.preparation.withLock {
                    check(!lease.closed.get()) { "Session lease is closed" }
                    if (declaredSize != null && declaredSize > limits.sourceBytes) throw SourceTooLargeException()
                    val target = File(lease.directory, UUID.randomUUID().toString())
                    var reserved = 0L
                    try {
                        target.outputStream().use { output ->
                            val buffer = ByteArray(limits.bufferBytes)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val count = stream.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                if (reserved + count > limits.sourceBytes) throw SourceTooLargeException()
                                synchronized(state) {
                                    check(!lease.closed.get()) { "Session lease is closed" }
                                    if (state.active.values.sum() + count > limits.scratchBytes ||
                                        usableSpace() - count < limits.freeReserveBytes) throw SourceSpaceException()
                                    state.active[lease.directory] = state.active.getValue(lease.directory) + count
                                    reserved += count
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        target
                    } catch (e: Throwable) {
                        target.delete()
                        synchronized(state) {
                            state.active[lease.directory]?.let { state.active[lease.directory] = it - reserved }
                        }
                        throw e
                    }
                }
            }
        }

    internal fun release(lease: SessionLease) = synchronized(state) {
        lease.directory.deleteRecursively()
        state.active.remove(lease.directory)
        Unit
    }

    companion object { private val states = ConcurrentHashMap<String, State>() }
}

class SessionLease internal constructor(val directory: File, private val workspace: SessionWorkspace) : AutoCloseable {
    internal val closed = AtomicBoolean()
    /** Consumes input ownership, including rejection, cancellation and copy failures. */
    suspend fun stage(input: InputStream, declaredSize: Long? = null): File = workspace.stage(this, input, declaredSize)
    override fun close() { if (closed.compareAndSet(false, true)) workspace.release(this) }
}
