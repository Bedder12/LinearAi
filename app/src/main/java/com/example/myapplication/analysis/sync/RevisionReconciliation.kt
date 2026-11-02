package com.example.myapplication.analysis.sync

import com.example.myapplication.analysis.transport.AnalysisRequestPayload

sealed interface RecoveryDecision {
    data class Resend(val request: AnalysisRequestPayload) : RecoveryDecision
    data object Stop : RecoveryDecision
}

/**
 * Rebuild the missing suffix only from an intact, ordered pending-change journal.
 * A revision number alone is not evidence that its events are complete: the caller
 * must preserve whole revision batches while they remain pending.
 */
fun reconcileRevisionGap(
    original: AnalysisRequestPayload,
    acceptedRevision: Long
): RecoveryDecision {
    if (acceptedRevision < 0 || acceptedRevision >= original.revision ||
        acceptedRevision == Long.MAX_VALUE
    ) return RecoveryDecision.Stop

    val suffix = original.changes.filter { it.revision > acceptedRevision }
    if (suffix.isEmpty()) return RecoveryDecision.Stop

    var expected = acceptedRevision + 1
    var previous = expected
    for (entry in suffix) {
        if (entry.revision < previous || entry.revision > expected) {
            return RecoveryDecision.Stop
        }
        if (entry.revision == expected) {
            previous = expected
            expected++
        }
    }
    if (expected - 1 != original.revision) return RecoveryDecision.Stop

    val resend = original.copy(changes = suffix)
    // A revision-gap response to the very same complete request cannot be
    // repaired by blindly replaying the identical payload.
    if (resend == original) return RecoveryDecision.Stop

    return RecoveryDecision.Resend(resend)
}
