package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.PendingInkChange
import com.example.myapplication.domain.StudentWork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRequestPayloadMapperTest {

    @Test
    fun studentWork_mapsSessionRevisionAndPendingChanges() {

        val studentWork = StudentWork(
            workSessionId = "550e8400-e29b-41d4-a716-446655440000",
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

        val result =
            studentWork.toAnalysisRequestPayload()

        assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            result.workSessionId
        )

        assertEquals(
            7L,
            result.revision
        )

        assertEquals(
            1,
            result.changes.size
        )

        val pendingChange =
            result.changes.first()

        assertEquals(
            7L,
            pendingChange.revision
        )

        assertTrue(
            pendingChange.change
                is InkChangePayload.StrokeRemoved
        )

        val removed =
            pendingChange.change
                as InkChangePayload.StrokeRemoved

        assertEquals(
            "stroke-123",
            removed.strokeId
        )
    }
}
