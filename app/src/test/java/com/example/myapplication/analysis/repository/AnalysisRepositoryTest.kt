package com.example.myapplication.analysis.repository

import com.example.myapplication.analysis.network.AnalysisApi
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
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class AnalysisRepositoryTest {
    private fun work() = StudentWork(
        workSessionId = "550e8400-e29b-41d4-a716-446655440000",
        revision = 7L,
        pendingChanges = listOf(
            PendingInkChange(
                revision = 7L,
                change = InkChange.StrokeRemoved(strokeId = "stroke-123")
            )
        )
    )

    @Test
    fun analyze_mapsStudentWork_callsApi_andReturnsSuccess() = runTest {
        var capturedRequest: AnalysisRequestPayload? = null
        val fakeApi = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                capturedRequest = request
                return AnalysisResponsePayload(acceptedRevision = 7L, status = "accepted")
            }
        }

        val result = AnalysisRepository(fakeApi).analyze(work())

        assertNotNull(capturedRequest)
        assertEquals(work().workSessionId, capturedRequest!!.workSessionId)
        assertEquals(7L, capturedRequest!!.revision)
        assertEquals(1, capturedRequest!!.changes.size)
        assertEquals(7L, capturedRequest!!.changes.first().revision)
        assertEquals(SyncResult.Success(7L), result)
    }

    @Test
    fun revisionGap_extractsServerRevision() = runTest {
        val result = AnalysisRepository(errorApi(
            409,
            """{"type":"/problems/revision-gap","status":409,"acceptedRevision":5}"""
        )).analyze(work())
        assertEquals(SyncResult.RevisionGap(5L), result)
    }

    @Test
    fun conflict_doesNotBecomeRetryable() = runTest {
        val result = AnalysisRepository(errorApi(
            409,
            """{"type":"/problems/revision-conflict","status":409}"""
        )).analyze(work())
        assertEquals(SyncResult.RevisionConflict, result)
    }

    @Test
    fun malformedGapBody_isNotTrusted() = runTest {
        val result = AnalysisRepository(errorApi(409, """{"detail":"gap"}"""))
            .analyze(work())
        assertEquals(SyncResult.UnexpectedFailure, result)
    }

    @Test
    fun invalidRequest_doesNotBecomeRetryable() = runTest {
        val result = AnalysisRepository(errorApi(
            422, """{"type":"/problems/invalid-ink-change"}"""
        )).analyze(work())
        assertEquals(SyncResult.InvalidRequest, result)
    }

    @Test
    fun serviceUnavailable_isTemporaryFailure() = runTest {
        assertEquals(
            SyncResult.TemporaryFailure,
            AnalysisRepository(errorApi(503, "{}")).analyze(work())
        )
    }

    @Test
    fun ioError_isTemporaryFailure() = runTest {
        val api = object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                throw IOException("connection interrupted")
            }
        }
        assertEquals(SyncResult.TemporaryFailure, AnalysisRepository(api).analyze(work()))
    }

    private fun errorApi(status: Int, body: String): AnalysisApi =
        object : AnalysisApi {
            override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                val errorBody = body.toResponseBody("application/problem+json".toMediaType())
                throw HttpException(Response.error<AnalysisResponsePayload>(status, errorBody))
            }
        }
}
