package com.example.myapplication.analysis

import com.example.myapplication.Domain.StudentWork

fun StudentWork.toAnalysisRequest(): AnalysisRequest {
    return AnalysisRequest(
        revision = revision,
        changes = pendingChanges.toList()
    )
}