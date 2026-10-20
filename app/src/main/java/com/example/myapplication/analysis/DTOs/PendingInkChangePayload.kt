package com.example.myapplication.analysis.DTOs

data class PendingInkChangePayload(
    val revision: Long,
    val change: InkChangePayload
)
