package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.PendingInkChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingInkChangePayloadMapperTest {

    @Test
    fun pendingChange_mapsRevisionAndNestedChange() {

        val pendingChange = PendingInkChange(
            revision = 7L,
            change = InkChange.StrokeRemoved(
                strokeId = "stroke-123"
            )
        )

        val result = pendingChange.toPayload()

        assertEquals(
            7L,
            result.revision
        )

        assertTrue(
            result.change is InkChangePayload.StrokeRemoved
        )

        val removed =
            result.change as InkChangePayload.StrokeRemoved

        assertEquals(
            "stroke-123",
            removed.strokeId
        )
    }
}