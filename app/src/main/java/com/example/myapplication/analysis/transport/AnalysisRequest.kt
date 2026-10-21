package com.example.myapplication.analysis.transport

data class AnalysisRequestPayload(
    val revision: Long,
    val changes: List<PendingInkChangePayload>
)