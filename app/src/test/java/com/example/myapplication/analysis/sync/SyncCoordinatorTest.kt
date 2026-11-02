package com.example.myapplication.analysis.sync

import com.example.myapplication.analysis.network.AnalysisApi
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.analysis.repository.SyncResult
import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.AnalysisResponsePayload
import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.PendingInkChange
import com.example.myapplication.domain.StudentWork
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SyncCoordinatorTest {
    private fun work(vararg revisions: Long) = StudentWork(
        workSessionId = "550e8400-e29b-41d4-a716-446655440000",
        revision = 7L,
        pendingChanges = revisions.map {
            PendingInkChange(it, InkChange.DocumentCleared)
        }
    )

    private fun gap(acceptedRevision: Long): Nothing {
        val body = """{"type":"/problems/revision-gap","acceptedRevision":$acceptedRevision}"""
            .toResponseBody("application/problem+json".toMediaType())
        throw HttpException(Response.error<AnalysisResponsePayload>(409, body))
    }

    @Test
    fun verifiesOverlappingEventsBeforeSubmittingMissingRevisions() = runTest {
        val requests = mutableListOf<AnalysisRequestPayload>()
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                requests.add(request)
                return when (requests.size) {
                    1 -> gap(5L)
                    2 -> AnalysisResponsePayload(5L, "accepted")
                    else -> AnalysisResponsePayload(7L, "accepted")
                }
            }
        }

        val result = SyncCoordinator(AnalysisRepository(api)).synchronize(work(5, 6, 7))
        assertEquals(SyncResult.Success(7L), result)
        assertEquals(listOf(7L, 5L, 7L), requests.map { it.revision })
        assertEquals(listOf(5L), requests[1].changes.map { it.revision })
        assertEquals(listOf(6L, 7L), requests[2].changes.map { it.revision })
    }

    @Test
    fun conflictDuringVerificationStopsWithoutResendingTail() = runTest {
        var calls = 0
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                calls++
                if (calls == 1) gap(5L)
                val body = """{"type":"/problems/revision-conflict"}"""
                    .toResponseBody("application/problem+json".toMediaType())
                throw HttpException(Response.error<AnalysisResponsePayload>(409, body))
            }
        }
        val result = SyncCoordinator(AnalysisRepository(api)).synchronize(work(5, 6, 7))
        assertEquals(SyncResult.RevisionConflict, result)
        assertEquals(2, calls)
    }

    @Test
    fun incompleteHistoryStopsAfterGap() = runTest {
        var calls = 0
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                calls++
                gap(5L)
            }
        }
        val result = SyncCoordinator(AnalysisRepository(api)).synchronize(work(5, 7))
        assertEquals(SyncResult.RevisionGap(5L), result)
        assertEquals(1, calls)
    }

    @Test
    fun timeoutRetriesTheIdenticalRequestWithBoundedDelays() = runTest {
        val requests = mutableListOf<AnalysisRequestPayload>()
        val pauses = mutableListOf<Long>()
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                requests.add(request)
                if (requests.size < 3) throw IOException("response lost")
                return AnalysisResponsePayload(7L, "accepted")
            }
        }
        val result = SyncCoordinator(AnalysisRepository(api), pause = { pauses.add(it) })
            .synchronize(work(6, 7))
        assertEquals(SyncResult.Success(7L), result)
        assertEquals(3, requests.size)
        assertEquals(requests[0], requests[1])
        assertEquals(requests[1], requests[2])
        assertEquals(listOf(200L, 400L), pauses)
    }

    @Test
    fun exhaustedRetriesReturnTemporaryFailure() = runTest {
        var calls = 0
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                calls++
                throw IOException("offline")
            }
        }
        val result = SyncCoordinator(AnalysisRepository(api), pause = {}).synchronize(work(6, 7))
        assertEquals(SyncResult.TemporaryFailure, result)
        assertEquals(3, calls)
    }
}
