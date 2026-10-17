package com.example.myapplication.ink

import androidx.ink.strokes.Stroke

sealed interface InkChange {

    data class StrokeAdded(
        val stroke: Stroke
    ) : InkChange

    data class StrokeRemoved(
        val stroke: Stroke
    ) : InkChange

    data object DocumentCleared : InkChange
}
