package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.PendingInkChange

fun PendingInkChange.toPayload(): PendingInkChangePayload {
    return PendingInkChangePayload(
        revision = revision,
        change = change.toPayload()
    )
}

data class PendingInkChangePayload(
    val revision: Long,
    val change: InkChangePayload
)