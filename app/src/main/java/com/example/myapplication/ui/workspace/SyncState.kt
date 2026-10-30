package com.example.myapplication.ui.workspace

sealed interface SyncState {
    data object Idle : SyncState
    data object Syncing : SyncState
    data object Synced : SyncState
    data class NeedsRecovery(val acceptedRevision: Long) : SyncState
    data object Conflict : SyncState
    data object InvalidRequest : SyncState
    data object TemporaryFailure : SyncState
    data object UnexpectedFailure : SyncState
}
