package com.example.myapplication.analysis.transport

import kotlinx.serialization.Serializable

@Serializable
data class AnalysisResponsePayload(
    val acceptedRevision: Long,
    val status: String
)