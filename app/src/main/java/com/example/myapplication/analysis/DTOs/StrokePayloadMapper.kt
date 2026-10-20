package com.example.myapplication.analysis.DTOs

import com.example.myapplication.Domain.TrackedStroke

fun TrackedStroke.toPayload(): StrokePayload {

    val inputs = stroke.inputs

    val points = (0 until inputs.size).map { index ->
        val input = inputs[index]

        StrokePointPayload(
            x = input.position.x,
            y = input.position.y,
            elapsedTimeMillis = input.elapsedTimeMillis
        )
    }

    return StrokePayload(
        id = id,
        toolType = inputs.toolType.toString(),
        points = points
    )
}