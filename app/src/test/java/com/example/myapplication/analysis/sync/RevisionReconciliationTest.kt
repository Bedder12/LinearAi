package com.example.myapplication.analysis.sync

import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.InkChangePayload
import com.example.myapplication.analysis.transport.PendingInkChangePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionReconciliationTest {
    private fun request(vararg revisions: Long) = AnalysisRequestPayload(
        workSessionId = "550e8400-e29b-41d4-a716-446655440000",
        revision = 7L,
        changes = revisions.map {
            PendingInkChangePayload(it, InkChangePayload.DocumentCleared)
        }
    )

    @Test
    fun overlappingRevisionsMustBeVerifiedBeforeResendingMissingSuffix() {
        val original = request(5, 6, 6, 7)
        val decision = reconcileRevisionGap(original, 5L)
        assertTrue(decision is RecoveryDecision.VerifyThenResend)
        decision as RecoveryDecision.VerifyThenResend
        assertEquals(5L, decision.verification.revision)
        assertEquals(listOf(5L), decision.verification.changes.map { it.revision })
        assertEquals(listOf(6L, 6L, 7L), decision.missing.changes.map { it.revision })
        assertEquals(7L, decision.missing.revision)
    }

    @Test
    fun missingRevisionCannotBeReconstructed() {
        assertEquals(RecoveryDecision.Stop, reconcileRevisionGap(request(5, 7), 5L))
    }

    @Test
    fun noOverlapMustNotResendIdenticalFailedRequest() {
        assertEquals(RecoveryDecision.Stop, reconcileRevisionGap(request(6, 7), 5L))
    }

    @Test
    fun unsortedEventsCannotBeReplayed() {
        assertEquals(RecoveryDecision.Stop, reconcileRevisionGap(request(6, 5, 7), 5L))
    }

    @Test
    fun serverAheadOfSnapshotCannotBeReconciled() {
        assertEquals(RecoveryDecision.Stop, reconcileRevisionGap(request(5, 6, 7), 8L))
    }
}
