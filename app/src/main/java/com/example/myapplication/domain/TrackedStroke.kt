package com.example.myapplication.domain

import androidx.ink.strokes.Stroke
import java.util.UUID

data class TrackedStroke(
    val id: String = UUID.randomUUID().toString(),
    val stroke: Stroke
)