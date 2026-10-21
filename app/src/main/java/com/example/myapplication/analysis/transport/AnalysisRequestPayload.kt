package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.StudentWork

fun StudentWork.toAnalysisRequestPayload(): AnalysisRequestPayload {
    return AnalysisRequestPayload(
        revision = revision,
        changes = pendingChanges.map { pendingChange ->
            pendingChange.toPayload()
        }
    )
}