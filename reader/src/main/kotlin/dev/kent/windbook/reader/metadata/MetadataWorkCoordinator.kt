package dev.kent.windbook.reader.metadata

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One metadata/cover parser across imports, folder preparation and outline backfill. */
class MetadataWorkCoordinator {
    private val mutex=Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
    companion object { val shared=MetadataWorkCoordinator() }
}
