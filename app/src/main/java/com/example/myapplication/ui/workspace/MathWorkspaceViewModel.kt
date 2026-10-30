package com.example.myapplication.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.analysis.repository.SyncResult
import com.example.myapplication.domain.StudentWork
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.ink.InkTool
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MathWorkspaceViewModel(
    private val analysisRepository: AnalysisRepository
) : ViewModel() {

    var studentWork by mutableStateOf(StudentWork())
        private set

    var selectedTool by mutableStateOf(InkTool.PEN)
        private set

    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set

    fun selectTool(tool: InkTool) {
        selectedTool = tool
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        studentWork = studentWork.addStrokes(strokes)
    }

    fun removeStrokes(strokes: Set<TrackedStroke>) {
        studentWork = studentWork.removeStrokes(strokes)
    }

    fun clear() {
        studentWork = studentWork.clear()
    }

    fun check() {
        // Never send overlapping snapshots for the same work session.
        // A conflict or gap needs explicit reconciliation, not blind resubmission.
        if (syncState is SyncState.Syncing ||
            syncState is SyncState.NeedsRecovery ||
            syncState is SyncState.Conflict ||
            syncState is SyncState.InvalidRequest
        ) return

        if (studentWork.pendingChanges.isEmpty()) {
            syncState = SyncState.Synced
            return
        }

        val snapshot = studentWork
        syncState = SyncState.Syncing

        viewModelScope.launch {
            try {
                when (val result = analysisRepository.analyze(snapshot)) {
                    is SyncResult.Success -> {
                        // Never clear local revisions beyond those sent in this snapshot.
                        studentWork = studentWork.markChangesSynced(result.acceptedRevision)
                        syncState = if (studentWork.pendingChanges.isEmpty()) {
                            SyncState.Synced
                        } else {
                            SyncState.Idle
                        }
                    }
                    is SyncResult.RevisionGap ->
                        syncState = SyncState.NeedsRecovery(result.acceptedRevision)
                    SyncResult.RevisionConflict -> syncState = SyncState.Conflict
                    SyncResult.InvalidRequest -> syncState = SyncState.InvalidRequest
                    SyncResult.TemporaryFailure -> syncState = SyncState.TemporaryFailure
                    SyncResult.UnexpectedFailure -> syncState = SyncState.UnexpectedFailure
                }
            } catch (exception: CancellationException) {
                syncState = SyncState.Idle
                throw exception
            }
        }
    }
}
