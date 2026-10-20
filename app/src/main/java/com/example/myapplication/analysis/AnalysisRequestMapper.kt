package com.example.myapplication.analysis

import com.example.myapplication.domain.StudentWork

fun StudentWork.toAnalysisRequest(): AnalysisRequest {
    return AnalysisRequest(
        revision = revision,
        changes = pendingChanges.toList()
    )
}