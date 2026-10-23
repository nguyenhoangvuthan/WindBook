package dev.kent.windbook.core.data

import dev.kent.windbook.core.model.BookSource

sealed interface Verification {
    data class Ready(val bookId: String, val source: BookSource) : Verification
    data class Redirect(val bookId: String) : Verification
    data class Changed(val newBookId: String) : Verification
    data class HiddenMatch(val bookId: String) : Verification
}

enum class RevisionDecision { READY, CHANGED, REDIRECT, HIDDEN }

object SourceRevisionPolicy {
    fun decide(oldSha: String?, sha: String, hasMatch: Boolean, hasReadingData: Boolean, hiddenMatch: Boolean): RevisionDecision =
        when {
            oldSha != null && oldSha != sha -> RevisionDecision.CHANGED
            hiddenMatch -> RevisionDecision.HIDDEN
            hasMatch && !hasReadingData -> RevisionDecision.REDIRECT
            else -> RevisionDecision.READY
        }
}
