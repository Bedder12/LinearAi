package com.example.myapplication.analysis.transport

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class InkChangeSerializationTest {
    private val json = Json { classDiscriminator = "type" }

    @Test
    fun serializesAllInkChangeVariantsWithApiDiscriminator() {
        val payload = AnalysisRequestPayload(
            workSessionId = "550e8400-e29b-41d4-a716-446655440000",
            revision = 3,
            changes = listOf(
                PendingInkChangePayload(
                    revision = 1,
                    change = InkChangePayload.StrokeAdded(
                        StrokePayload(
                            id = "s1",
                            toolType = "pen",
                            points = listOf(StrokePointPayload(1f, 2f, 3))
                        )
                    )
                ),
                PendingInkChangePayload(
                    revision = 2,
                    change = InkChangePayload.StrokeRemoved("s1")
                ),
                PendingInkChangePayload(
                    revision = 3,
                    change = InkChangePayload.DocumentCleared
                )
            )
        )

        val encoded = json.encodeToString(payload)
        val changes = json.parseToJsonElement(encoded).jsonObject
            .getValue("changes").jsonArray.map { it.jsonObject.getValue("change").jsonObject }

        assertEquals("stroke_added", changes[0].getValue("type").jsonPrimitive.content)
        assertEquals("s1", changes[0].getValue("stroke").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("stroke_removed", changes[1].getValue("type").jsonPrimitive.content)
        assertEquals("s1", changes[1].getValue("strokeId").jsonPrimitive.content)
        assertEquals("document_cleared", changes[2].getValue("type").jsonPrimitive.content)

        assertEquals(payload, json.decodeFromString<AnalysisRequestPayload>(encoded))
    }
}
