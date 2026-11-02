package com.example.myapplication.analysis.sync

import com.example.myapplication.analysis.transport.AnalysisRequestPayload

sealed interface RecoveryDecision {
    data class VerifyThenResend(
        val verification: AnalysisRequestPayload,
        val missing: AnalysisRequestPayload
    ) : RecoveryDecision

    data object Stop : RecoveryDecision
}

/**
 * Reconcile only if the original pending journal contains every new revision
 * and some older pending revisions to verify against committed history.
 *
 * This relies on StudentWork preserving complete revision batches. A numeric
 * revision alone cannot prove that individual stroke events are complete.
 */
fun reconcileRevisionGap(
    original: AnalysisRequestPayload,
    acceptedRevision: Long
): RecoveryDecision {
    if (acceptedRevision < 0 || acceptedRevision >= original.revision ||
        original.revision == Long.MAX_VALUE
    ) return RecoveryDecision.Stop

    val overlapping = original.changes.filter { it.revision <= acceptedRevision }
    val missing = original.changes.filter { it.revision > acceptedRevision }

    // No overlap means the original complete request would be identical on
    // replay. Missing overlap means there is nothing to verify first.
    if (overlapping.isEmpty() || missing.isEmpty()) return RecoveryDecision.Stop

    var last = 0L
    for (change in original.changes) {
        if (change.revision < 1 || change.revision < last ||
            change.revision > original.revision
        ) return RecoveryDecision.Stop
        last = change.revision
    }

    // Require a contiguous suffix; allow multiple events within one revision.
    var expected = acceptedRevision + 1
    for (change in missing) {
        if (change.revision == expected) {
            expected++
        } else if (change.revision != expected - 1) {
            return RecoveryDecision.Stop
        }
    }
    if (expected - 1 != original.revision) return RecoveryDecision.Stop

    return RecoveryDecision.VerifyThenResend(
        verification = original.copy(revision = acceptedRevision, changes = overlapping),
        missing = original.copy(changes = missing)
    )
}
