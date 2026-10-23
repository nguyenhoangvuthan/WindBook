package dev.kent.windbook.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRevisionPolicyTest {
    @Test fun changedFingerprint_alwaysCreatesRevision() {
        assertEquals(RevisionDecision.CHANGED, SourceRevisionPolicy.decide("old", "new", true, true, true))
    }
    @Test fun hiddenMatch_requiresRestore() {
        assertEquals(RevisionDecision.HIDDEN, SourceRevisionPolicy.decide(null, "new", true, false, true))
    }
    @Test fun readConflict_isNeverMerged() {
        assertEquals(RevisionDecision.READY, SourceRevisionPolicy.decide(null, "new", true, true, false))
    }
    @Test fun unreadDuplicate_redirects() {
        assertEquals(RevisionDecision.REDIRECT, SourceRevisionPolicy.decide(null, "new", true, false, false))
    }
}
