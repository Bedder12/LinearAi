package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequestPayload(
    val workSessionId: String,
    val revision: Long,
    val changes: List<PendingInkChangePayload>
)
