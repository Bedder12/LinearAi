package com.example.myapplication.data.local.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.domain.persistence.WorkspaceRepositoryException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.zip.CRC32

internal class LocalInkStrokeCodec {
    fun encode(trackedStroke: TrackedStroke): ByteArray {
        val brush = trackedStroke.stroke.brush
        val brushFamilyId = familyId(brush.family)
        val encodedInputs = ByteArrayOutputStream().use { output ->
            trackedStroke.stroke.inputs.encode(output)
            output.toByteArray()
        }
        require(encodedInputs.size in 1..MAX_INPUT_BYTES) {
            "Encoded stroke input size is outside the supported range"
        }

        val body = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(trackedStroke.id)
                output.writeUTF(brushFamilyId)
                output.writeLong(brush.colorLong)
                output.writeFloat(brush.size)
                output.writeFloat(brush.epsilon)
                output.writeInt(encodedInputs.size)
                output.write(encodedInputs)
            }
            bytes.toByteArray()
        }
        val checksum = CRC32().apply { update(body) }.value
        return ByteArrayOutputStream(body.size + CHECKSUM_BYTES).use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(body)
                output.writeLong(checksum)
            }
            bytes.toByteArray()
        }
    }

    fun decode(expectedStrokeId: String, codecVersion: Int, encoded: ByteArray): TrackedStroke {
        if (codecVersion != VERSION) {
            throw UnsupportedInkCodecVersionException(codecVersion)
        }
        if (encoded.size <= CHECKSUM_BYTES || encoded.size > MAX_RECORD_BYTES) {
            throw CorruptInkStrokeException("Stored stroke record has an invalid size")
        }

        val body = encoded.copyOfRange(0, encoded.size - CHECKSUM_BYTES)
        val storedChecksum = DataInputStream(
            ByteArrayInputStream(encoded, encoded.size - CHECKSUM_BYTES, CHECKSUM_BYTES)
        ).use(DataInputStream::readLong)
        val actualChecksum = CRC32().apply { update(body) }.value
        if (storedChecksum != actualChecksum) {
            throw CorruptInkStrokeException("Stored stroke checksum does not match")
        }

        try {
            DataInputStream(ByteArrayInputStream(body)).use { input ->
                if (input.readInt() != MAGIC) {
                    throw CorruptInkStrokeException("Stored stroke record has an invalid signature")
                }
                val recordVersion = input.readInt()
                if (recordVersion != VERSION) {
                    throw UnsupportedInkCodecVersionException(recordVersion)
                }
                val strokeId = input.readUTF()
                if (strokeId != expectedStrokeId) {
                    throw CorruptInkStrokeException("Stored stroke ID does not match its database key")
                }
                val familyId = input.readUTF()
                val colorLong = input.readLong()
                val size = input.readFloat()
                val epsilon = input.readFloat()
                val inputSize = input.readInt()
                if (inputSize !in 1..MAX_INPUT_BYTES || inputSize > input.available()) {
                    throw CorruptInkStrokeException("Stored stroke input length is invalid")
                }
                if (!size.isFinite() || size <= 0f || !epsilon.isFinite() || epsilon <= 0f) {
                    throw CorruptInkStrokeException("Stored brush parameters are invalid")
                }
                val inputBytes = ByteArray(inputSize)
                input.readFully(inputBytes)
                if (input.available() != 0) {
                    throw CorruptInkStrokeException("Stored stroke record has trailing data")
                }

                val inputs = ByteArrayInputStream(inputBytes).use { encodedInput ->
                    StrokeInputBatch.decode(encodedInput).also {
                        if (encodedInput.available() != 0) {
                            throw CorruptInkStrokeException("Stored Ink input has trailing data")
                        }
                    }
                }
                val brush = Brush.createWithColorLong(
                    family = familyFor(familyId),
                    colorLong = colorLong,
                    size = size,
                    epsilon = epsilon
                )
                return TrackedStroke(
                    id = strokeId,
                    stroke = Stroke(brush = brush, inputs = inputs)
                )
            }
        } catch (exception: LocalInkStrokeException) {
            throw exception
        } catch (exception: IOException) {
            throw CorruptInkStrokeException("Stored stroke record is truncated or malformed", exception)
        } catch (exception: IllegalArgumentException) {
            throw CorruptInkStrokeException("Stored stroke contains invalid Ink data", exception)
        }
    }

    private fun familyId(family: BrushFamily): String =
        when (family) {
            StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1) ->
                PRESSURE_PEN_V1
            else -> throw UnsupportedInkBrushFamilyException(family.toString())
        }

    private fun familyFor(familyId: String): BrushFamily =
        when (familyId) {
            PRESSURE_PEN_V1 -> StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1)
            else -> throw UnsupportedInkBrushFamilyException(familyId)
        }

    companion object {
        const val VERSION = 1
        private const val MAGIC = 0x4C41494E
        private const val PRESSURE_PEN_V1 = "stock:pressure-pen:v1"
        private const val CHECKSUM_BYTES = Long.SIZE_BYTES
        private const val MAX_INPUT_BYTES = 4 * 1024 * 1024
        private const val MAX_RECORD_BYTES = MAX_INPUT_BYTES + 128 * 1024
    }
}

sealed class LocalInkStrokeException(
    message: String,
    cause: Throwable? = null
) : WorkspaceRepositoryException(message, cause)

class UnsupportedInkBrushFamilyException(familyId: String) :
    LocalInkStrokeException("Unsupported AndroidX Ink brush family: $familyId")

class UnsupportedInkCodecVersionException(version: Int) :
    LocalInkStrokeException("Unsupported local Ink codec version: $version")

class CorruptInkStrokeException(
    message: String,
    cause: Throwable? = null
) : LocalInkStrokeException(message, cause)
