package com.example.myapplication.analysis.repository

import com.example.myapplication.analysis.network.AnalysisApi
import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.AnalysisResponsePayload
import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.PendingInkChange
import com.example.myapplication.domain.StudentWork
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AnalysisRepositoryTest {

    @Test
    fun analyze_mapsStudentWork_callsApi_andReturnsResponse() = runTest {
        var capturedRequest: AnalysisRequestPayload? = null

        val fakeApi = object : AnalysisApi {
            override suspend fun check(
                request: AnalysisRequestPayload
            ): AnalysisResponsePayload {
                capturedRequest = request

                return AnalysisResponsePayload(
                    acceptedRevision = 7L,
                    status = "accepted"
                )
            }
        }

        val repository = AnalysisRepository(
            api = fakeApi
        )

        val studentWork = StudentWork(
            revision = 7L,
            pendingChanges = listOf(
                PendingInkChange(
                    revision = 7L,
                    change = InkChange.StrokeRemoved(
                        strokeId = "stroke-123"
                    )
                )
            )
        )

        val response = repository.analyze(studentWork)

        assertNotNull(capturedRequest)
        assertEquals(7L, capturedRequest!!.revision)
        assertEquals(1, capturedRequest!!.changes.size)
        assertEquals(7L, capturedRequest!!.changes.first().revision)

        assertEquals(7L, response.acceptedRevision)
        assertEquals("accepted", response.status)
    }
}