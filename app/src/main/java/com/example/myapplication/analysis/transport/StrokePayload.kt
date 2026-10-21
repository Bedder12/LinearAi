package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
data class StrokePayload(
    val id: String,
    val toolType: String,
    val points: List<StrokePointPayload>
)