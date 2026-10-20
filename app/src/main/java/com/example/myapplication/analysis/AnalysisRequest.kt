package com.example.myapplication.analysis

import com.example.myapplication.domain.PendingInkChange

data class AnalysisRequest(
    val revision: Long,
    val changes: List<PendingInkChange>
)
