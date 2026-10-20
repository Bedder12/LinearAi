package com.example.myapplication.analysis.DTOs

import com.example.myapplication.domain.TrackedStroke

fun TrackedStroke.toPayload(): StrokePayload {

    val inputs = stroke.inputs

    val points = (0 until inputs.size).map { index ->
        val input = inputs[index]

        StrokePointPayload(
            x = input.x,
            y = input.y,
            elapsedTimeMillis = input.elapsedTimeMillis
        )
    }

    return StrokePayload(
        id = id,
        toolType = inputs.getToolType().toString(),
        points = points
    )
}