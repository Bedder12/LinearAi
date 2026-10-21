package com.example.myapplication.analysis.transport

sealed interface InkChangePayload{

    data class StrokeAdded(
        val stroke: StrokePayload
    ) : InkChangePayload

    data class StrokeRemoved(
        val strokeId: String
    ) : InkChangePayload

    data object DocumentCleared : InkChangePayload
}