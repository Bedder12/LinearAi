package com.example.myapplication.analysis.DTOs

sealed interface InkChangePayload{

    data class StrokeAdded(
        val stroke: StrokePayload
    ) : InkChangePayload

    data class StrokeRemoved(
        val strokeId: String
    ) : InkChangePayload

    data object DocumentCleared : InkChangePayload
}
