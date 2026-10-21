package com.example.myapplication.analysis.transport

data class StrokePayload(
    val id: String,
    val toolType: String,
    val points: List<StrokePointPayload>
)