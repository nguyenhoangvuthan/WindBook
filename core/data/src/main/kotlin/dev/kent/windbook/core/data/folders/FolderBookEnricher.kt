package dev.kent.windbook.core.data.folders

import dev.kent.windbook.core.data.db.BookSourceEntity
import dev.kent.windbook.core.model.BookFormat

fun interface FolderBookEnricher {
    suspend fun enrich(runId: String)
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
data class FolderMetadataCommit(val accepted: Boolean, val supersededCoverPath: String? = null)
