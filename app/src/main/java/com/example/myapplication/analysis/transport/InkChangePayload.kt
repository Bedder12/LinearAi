package com.example.myapplication.analysis.transport

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface InkChangePayload {

    @Serializable
    @SerialName("stroke_added")
    data class StrokeAdded(
        val stroke: StrokePayload
    ) : InkChangePayload

    @Serializable
    @SerialName("stroke_removed")
    data class StrokeRemoved(
        val strokeId: String
    ) : InkChangePayload

    @Serializable
    @SerialName("document_cleared")
    data object DocumentCleared : InkChangePayload
}
