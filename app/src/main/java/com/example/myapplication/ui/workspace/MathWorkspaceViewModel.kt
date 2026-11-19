package com.example.myapplication.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.domain.persistence.WorkspaceRepository
import com.example.myapplication.domain.persistence.WorkspaceRepositoryException
import com.example.myapplication.domain.persistence.WorkspaceSnapshot
import com.example.myapplication.ink.InkTool
import java.util.ArrayDeque
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

sealed interface WorkspaceUiState {
    data object Loading : WorkspaceUiState
    data class Ready(val snapshot: WorkspaceSnapshot) : WorkspaceUiState
    data class Saving(
        val snapshot: WorkspaceSnapshot,
        val queuedEditCount: Int
    ) : WorkspaceUiState
    data class LocalSaveError(
        val snapshot: WorkspaceSnapshot?,
        val queuedEditCount: Int,
        val message: String
    ) : WorkspaceUiState
}

sealed interface RemoteSyncState {
    data object Checking : RemoteSyncState
    data object NoPendingChanges : RemoteSyncState
    data class Pending(val batchCount: Int) : RemoteSyncState
    data object Unavailable : RemoteSyncState
}

class MathWorkspaceViewModel(
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {
    private val queuedEdits = ArrayDeque<WorkspaceEdit>()
    private var committedSnapshot: WorkspaceSnapshot? = null
    private var editInFlight = false

    var workspaceState by mutableStateOf<WorkspaceUiState>(WorkspaceUiState.Loading)
        private set

    var remoteSyncState by mutableStateOf<RemoteSyncState>(RemoteSyncState.Checking)
        private set

    var selectedTool by mutableStateOf(InkTool.PEN)
        private set

    init {
        loadWorkspace()
    }

    fun selectTool(tool: InkTool) {
        selectedTool = tool
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        if (strokes.isEmpty()) return
        enqueue(WorkspaceEdit.Add(strokes.map { TrackedStroke(stroke = it) }))
    }

    fun removeStrokes(strokes: Set<TrackedStroke>) {
        if (strokes.isEmpty()) return
        enqueue(WorkspaceEdit.Remove(strokes.toList()))
    }

    fun clear() {
        enqueue(WorkspaceEdit.Clear)
    }

    fun retryLocalSave() {
        val errorState = workspaceState as? WorkspaceUiState.LocalSaveError ?: return
        if (errorState.snapshot == null) {
            loadWorkspace()
            return
        }

        workspaceState = WorkspaceUiState.Ready(errorState.snapshot)
        processQueuedEdits()
    }

    private fun loadWorkspace() {
        workspaceState = WorkspaceUiState.Loading
        viewModelScope.launch {
            try {
                val snapshot = workspaceRepository.createOrLoadWorkspace()
                committedSnapshot = snapshot
                workspaceState = WorkspaceUiState.Ready(snapshot)
                refreshRemoteSyncState(snapshot.revision)
                processQueuedEdits()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: WorkspaceRepositoryException) {
                remoteSyncState = RemoteSyncState.Unavailable
                workspaceState = WorkspaceUiState.LocalSaveError(
                    snapshot = committedSnapshot,
                    queuedEditCount = queuedEdits.size,
                    message = exception.message ?: "The local workspace could not be loaded."
                )
            }
        }
    }

    private fun enqueue(edit: WorkspaceEdit) {
        queuedEdits.addLast(edit)
        processQueuedEdits()
    }

    private fun processQueuedEdits() {
        if (editInFlight || queuedEdits.isEmpty()) return
        if (workspaceState is WorkspaceUiState.Loading ||
            workspaceState is WorkspaceUiState.LocalSaveError
        ) return

        val currentSnapshot = committedSnapshot ?: return
        val edit = queuedEdits.peekFirst() ?: return
        editInFlight = true
        workspaceState = WorkspaceUiState.Saving(
            snapshot = currentSnapshot,
            queuedEditCount = queuedEdits.size
        )

        viewModelScope.launch {
            try {
                val savedSnapshot = edit.apply(workspaceRepository)
                if (savedSnapshot.documentId != currentSnapshot.documentId ||
                    savedSnapshot.pageId != currentSnapshot.pageId ||
                    savedSnapshot.workSessionId != currentSnapshot.workSessionId
                ) {
                    throw WorkspaceRepositoryException(
                        "A local edit changed the active workspace identity."
                    )
                }
                queuedEdits.removeFirst()
                committedSnapshot = savedSnapshot
                workspaceState = WorkspaceUiState.Ready(savedSnapshot)
                editInFlight = false
                if (queuedEdits.isNotEmpty()) {
                    processQueuedEdits()
                } else {
                    refreshRemoteSyncState(savedSnapshot.revision)
                }
            } catch (exception: CancellationException) {
                editInFlight = false
                throw exception
            } catch (exception: WorkspaceRepositoryException) {
                editInFlight = false
                workspaceState = WorkspaceUiState.LocalSaveError(
                    snapshot = committedSnapshot,
                    queuedEditCount = queuedEdits.size,
                    message = exception.message ?: "The local save failed."
                )
            }
        }
    }

    private fun refreshRemoteSyncState(expectedRevision: Long) {
        viewModelScope.launch {
            try {
                val pendingBatchCount = workspaceRepository.readPendingBatches().size
                if (committedSnapshot?.revision != expectedRevision) return@launch
                remoteSyncState = if (pendingBatchCount == 0) {
                    RemoteSyncState.NoPendingChanges
                } else {
                    RemoteSyncState.Pending(pendingBatchCount)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: WorkspaceRepositoryException) {
                if (committedSnapshot?.revision == expectedRevision) {
                    remoteSyncState = RemoteSyncState.Unavailable
                }
            }
        }
    }

    private sealed interface WorkspaceEdit {
        suspend fun apply(repository: WorkspaceRepository): WorkspaceSnapshot

        data class Add(val strokes: List<TrackedStroke>) : WorkspaceEdit {
            override suspend fun apply(repository: WorkspaceRepository) =
                repository.addTrackedStrokes(strokes)
        }

        data class Remove(val strokes: List<TrackedStroke>) : WorkspaceEdit {
            override suspend fun apply(repository: WorkspaceRepository) =
                repository.removeStrokes(strokes)
        }

        data object Clear : WorkspaceEdit {
            override suspend fun apply(repository: WorkspaceRepository) = repository.clear()
        }
    }
}
