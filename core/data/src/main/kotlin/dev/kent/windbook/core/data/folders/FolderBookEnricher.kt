package dev.kent.windbook.core.data.folders

import dev.kent.windbook.core.data.db.BookSourceEntity
import dev.kent.windbook.core.model.BookFormat

fun interface FolderBookEnricher {
    suspend fun enrich(runId: String)

    /** IDs are delivered only after their discovery transaction commits. */
    suspend fun enrichBatch(runId: String, sourceIds: List<String>) { }

    /** The coordinator joins every producer/consumer before retiring run-local scratch state. */
    suspend fun cleanup(runId: String) { }
}

/** Immutable run/source/book snapshot; publishing must validate it again atomically. */
data class FolderCoverCandidate(
    val runId: String,
    val source: BookSourceEntity,
    val format: BookFormat,
    val bookSha256: String?,
    val coverPath: String?,
    val relativePath: String,
)

/** Ownership of the newly staged file transfers only when accepted is true. */
data class FolderMetadataCommit(
    val accepted: Boolean,
    val supersededCoverPath: String? = null,
    val committedCandidate: FolderCoverCandidate? = null,
)
