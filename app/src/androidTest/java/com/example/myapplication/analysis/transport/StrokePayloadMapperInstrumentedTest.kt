package com.example.myapplication.analysis.transport

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.ink.InkBrushes
import org.junit.Assert.assertEquals
import org.junit.Test

class StrokePayloadMapperInstrumentedTest {

    @Test
    fun trackedStroke_mapsStableIdToolTypeAndPoints() {

        val inputs = MutableStrokeInputBatch()
            .add(
                InputToolType.STYLUS,
                10f,
                20f,
                0L,
                1f,
                0.5f,
                0f,
                0f
            )
            .add(
                InputToolType.STYLUS,
                30f,
                40f,
                10L,
                1f,
                0.5f,
                0f,
                0f
            )

        val stroke = Stroke(
            brush = InkBrushes.defaultPen,
            inputs = inputs
        )

        val trackedStroke = TrackedStroke(
            id = "stroke-123",
            stroke = stroke
        )

        val result = trackedStroke.toPayload()

        assertEquals(
            "stroke-123",
            result.id
        )

        assertEquals(
            InputToolType.STYLUS.toString(),
            result.toolType
        )

        assertEquals(
            2,
            result.points.size
        )

        assertEquals(
            10f,
            result.points[0].x
        )

        assertEquals(
            20f,
            result.points[0].y
        )

        assertEquals(
            0L,
            result.points[0].elapsedTimeMillis
        )

        assertEquals(
            30f,
            result.points[1].x
        )

        assertEquals(
            40f,
            result.points[1].y
        )

        assertEquals(
            10L,
            result.points[1].elapsedTimeMillis
        )
    }
}