package com.example.myapplication

import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.StudentWork
import com.example.myapplication.ink.InkBrushes
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class StudentWorkInstrumentedTest {

    @Test
    fun addStrokes_addsStrokeAndCreatesPendingChange() {

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
                20f,
                30f,
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

        val work = StudentWork()

        val result = work.addStrokes(
            listOf(stroke)
        )

        assertEquals(1L, result.revision)
        assertEquals(1, result.document.strokes.size)
        assertEquals(1, result.pendingChanges.size)

        val trackedStroke = result.document.strokes.first()

        val change =
            result.pendingChanges.first().change

        assertTrue(change is InkChange.StrokeAdded)

        val addedChange =
            change as InkChange.StrokeAdded

        assertEquals(
            trackedStroke.id,
            addedChange.stroke.id
        )
    }

    @Test
    fun removeStrokes_removesStrokeAndCreatesPendingRemoval() {

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
                20f,
                30f,
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

        val workWithStroke = StudentWork()
            .addStrokes(listOf(stroke))

        val trackedStroke =
            workWithStroke.document.strokes.first()

        val result = workWithStroke.removeStrokes(
            setOf(trackedStroke)
        )

        assertEquals(2L, result.revision)
        assertTrue(result.document.strokes.isEmpty())
        assertEquals(2, result.pendingChanges.size)

        val removalChange =
            result.pendingChanges.last().change

        assertTrue(removalChange is InkChange.StrokeRemoved)

        val removed =
            removalChange as InkChange.StrokeRemoved

        assertEquals(
            trackedStroke.id,
            removed.strokeId
        )
    }
}