package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisRequestPayload(
    val revision: Long,
    val changes: List<PendingInkChangePayload>
)