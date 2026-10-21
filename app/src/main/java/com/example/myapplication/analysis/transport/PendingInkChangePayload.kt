package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.PendingInkChange
import kotlinx.serialization.Serializable

fun PendingInkChange.toPayload(): PendingInkChangePayload {
    return PendingInkChangePayload(
        revision = revision,
        change = change.toPayload()
    )
}

@Serializable
data class PendingInkChangePayload(
    val revision: Long,
    val change: InkChangePayload
)