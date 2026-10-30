package com.example.myapplication.analysis.repository

sealed interface SyncResult {
    data class Success(val acceptedRevision: Long) : SyncResult
    data class RevisionGap(val acceptedRevision: Long) : SyncResult
    data object RevisionConflict : SyncResult
    data object InvalidRequest : SyncResult
    data object TemporaryFailure : SyncResult
    data object UnexpectedFailure : SyncResult
}
