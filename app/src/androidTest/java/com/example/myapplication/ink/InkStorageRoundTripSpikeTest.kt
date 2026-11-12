package com.example.myapplication.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S03 feasibility spike: disk round-trip using AndroidX Ink 1.0.0.
 *
 * Isolated to androidTest. This is not a production serializer, Room implementation,
 * crash/process-death test, or proof of pixel-identical rendering.
 */
@RunWith(AndroidJUnit4::class)
class InkStorageRoundTripSpikeTest {

    @Test
    fun stylusStrokeSurvivesFileRoundTripWithIdentityInputsAndBrush() {
        val originalId = "stable-stroke-123"
        val inputs = MutableStrokeInputBatch().apply {
            add(type = InputToolType.STYLUS, x = 12.5f, y = 20.25f,
                elapsedTimeMillis = 0L, pressure = 0.3f,
                tiltRadians = 0.2f, orientationRadians = 0.4f)
            add(type = InputToolType.STYLUS, x = 17.0f, y = 23.75f,
                elapsedTimeMillis = 16L, pressure = 0.7f,
                tiltRadians = 0.3f, orientationRadians = 0.6f)
            add(type = InputToolType.STYLUS, x = 29.75f, y = 34.5f,
                elapsedTimeMillis = 32L, pressure = 0.5f,
                tiltRadians = 0.4f, orientationRadians = 0.8f)
        }
        val original = Stroke(brush = InkBrushes.defaultPen, inputs = inputs)
        val file = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "s03-ink-round-trip-spike.bin"
        )

        try {
            val encodedInputs = ByteArrayOutputStream().use { output ->
                original.inputs.encode(output)
                output.toByteArray()
            }

            // Explicit test-only envelope. Production format, checksums, migrations
            // and brush family registry must be designed separately for Room.
            DataOutputStream(file.outputStream().buffered()).use { output ->
                output.writeInt(1) // test record version
                output.writeUTF(originalId)
                output.writeUTF("stock:pressurePen")
                output.writeLong(original.brush.colorLong)
                output.writeFloat(original.brush.size)
                output.writeFloat(original.brush.epsilon)
                output.writeInt(encodedInputs.size)
                output.write(encodedInputs)
            }

            val (restoredId, restoredStroke) =
                DataInputStream(file.inputStream().buffered()).use { input ->
                    assertEquals(1, input.readInt())
                    val id = input.readUTF()
                    assertEquals("stock:pressurePen", input.readUTF())
                    val color = input.readLong()
                    val size = input.readFloat()
                    val epsilon = input.readFloat()
                    val inputLength = input.readInt()
                    assertTrue(inputLength in 1..1_000_000)
                    val restoredBytes = ByteArray(inputLength)
                    input.readFully(restoredBytes)
                    val decoded = ByteArrayInputStream(restoredBytes).use {
                        StrokeInputBatch.decode(it)
                    }
                    val restoredBrush = Brush.createWithColorLong(
                        family = StockBrushes.pressurePen(),
                        colorLong = color,
                        size = size,
                        epsilon = epsilon
                    )
                    id to Stroke(brush = restoredBrush, inputs = decoded)
                }

            assertEquals(originalId, restoredId)
            assertEquals(original.brush.colorLong, restoredStroke.brush.colorLong)
            assertEquals(original.brush.size, restoredStroke.brush.size, 0f)
            assertEquals(original.brush.epsilon, restoredStroke.brush.epsilon, 0f)
            assertEquals(original.inputs.size, restoredStroke.inputs.size)
            assertEquals(original.inputs.getToolType(), restoredStroke.inputs.getToolType())

            // Ink storage uses compressed encoding: validate fidelity with an
            // explicit numerical tolerance rather than assuming byte equality.
            for (index in 0 until original.inputs.size) {
                val expected = original.inputs[index]
                val actual = restoredStroke.inputs[index]
                assertEquals(expected.x, actual.x, 0.1f)
                assertEquals(expected.y, actual.y, 0.1f)
                assertEquals(expected.elapsedTimeMillis, actual.elapsedTimeMillis)
                assertEquals(expected.pressure, actual.pressure, 0.05f)
                assertEquals(expected.tiltRadians, actual.tiltRadians, 0.05f)
                assertEquals(expected.orientationRadians, actual.orientationRadians, 0.05f)
            }
        } finally {
            file.delete()
        }
    }
}
