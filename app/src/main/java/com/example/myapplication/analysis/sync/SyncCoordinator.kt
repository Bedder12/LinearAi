package com.example.myapplication.analysis.sync

import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.analysis.repository.SyncResult
import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.toAnalysisRequestPayload
import com.example.myapplication.domain.StudentWork
import kotlinx.coroutines.delay

/**
 * Executes immutable requests and allows at most one attempt to reconcile a gap.
 * A conflict or incomplete history is never retried automatically.
 */
class SyncCoordinator(
    private val repository: AnalysisRepository,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    suspend fun synchronize(snapshot: StudentWork): SyncResult {
        val original = snapshot.toAnalysisRequestPayload()
        if (original.changes.isEmpty()) return SyncResult.Success(original.revision)

        val initialResult = sendWithRetries(original)
        if (initialResult !is SyncResult.RevisionGap) return initialResult

        return when (val decision = reconcileRevisionGap(original, initialResult.acceptedRevision)) {
            is RecoveryDecision.VerifyThenResend -> {
                // A gap was checked before overlapping retries by the backend.
                // Verify the already-accepted entries before trying the missing tail.
                when (val verified = sendWithRetries(decision.verification)) {
                    is SyncResult.Success -> sendWithRetries(decision.missing)
                    else -> verified
                }
            }
            RecoveryDecision.Stop -> initialResult
        }
    }

    private suspend fun sendWithRetries(request: AnalysisRequestPayload): SyncResult {
        // Three total attempts, bounded delay, and exactly the same payload on retry.
        repeat(3) { attempt ->
            val result = repository.analyze(request)
            if (result != SyncResult.TemporaryFailure || attempt == 2) return result
            pause(if (attempt == 0) 200L else 400L)
        }
        error("Unreachable")
    }
}
