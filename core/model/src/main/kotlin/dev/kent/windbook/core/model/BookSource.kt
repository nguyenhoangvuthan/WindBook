package dev.kent.windbook.core.model

sealed interface BookSource {
    data class Owned(val path: String) : BookSource
    data class External(val uri: String, val key: DocumentKey) : BookSource
}

/** Provider identity stays stable when one document is discovered through overlapping trees. */
data class DocumentKey(val authority: String, val documentId: String)

enum class SourceAvailability {
    UNVERIFIED,
    AVAILABLE,
    MISSING,
    ACCESS_DENIED,
    DISCONNECTED,
    UNREADABLE,
}
