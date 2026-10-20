package com.example.myapplication.Domain

sealed interface InkChange {

    data class StrokeAdded(
        val stroke: TrackedStroke
    ) : InkChange

    data class StrokeRemoved(
        val strokeId: String
    ) : InkChange

    data object DocumentCleared : InkChange
}