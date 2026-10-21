package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
sealed interface InkChangePayload{

    data class StrokeAdded(
        val stroke: StrokePayload
    ) : InkChangePayload

    data class StrokeRemoved(
        val strokeId: String
    ) : InkChangePayload

    data object DocumentCleared : InkChangePayload
}