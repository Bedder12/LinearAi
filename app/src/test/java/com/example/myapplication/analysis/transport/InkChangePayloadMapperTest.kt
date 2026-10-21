package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.InkChange
import org.junit.Assert
import org.junit.Test

class InkChangePayloadMapperTest {

    @Test
    fun strokeRemoved_mapsToStrokeRemovedPayload() {

        val change = InkChange.StrokeRemoved(
            strokeId = "stroke-123"
        )

        val result = change.toPayload()

        Assert.assertTrue(
            result is InkChangePayload.StrokeRemoved
        )

        val removed =
            result as InkChangePayload.StrokeRemoved

        Assert.assertEquals(
            "stroke-123",
            removed.strokeId
        )
    }

    @Test
    fun documentCleared_mapsToDocumentClearedPayload() {

        val change = InkChange.DocumentCleared

        val result = change.toPayload()

        Assert.assertEquals(
            InkChangePayload.DocumentCleared,
            result
        )
    }
}