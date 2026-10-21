package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
data class StrokePointPayload(
    val x: Float,
    val y: Float,
    val elapsedTimeMillis: Long
)