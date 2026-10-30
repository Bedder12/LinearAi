package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.analysis.network.AnalysisNetwork
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.ink.InkSurface
import com.example.myapplication.ink.InkTool
import com.example.myapplication.ui.workspace.MathWorkspaceViewModel
import com.example.myapplication.ui.workspace.MathWorkspaceViewModelFactory
import com.example.myapplication.ui.workspace.SyncState

@Composable
fun MathWorkspaceScreen() {

    val viewModel: MathWorkspaceViewModel = viewModel(
        factory = MathWorkspaceViewModelFactory(
            analysisRepository = AnalysisRepository(
                api = AnalysisNetwork.api
            )
        )
    )

    val studentWork = viewModel.studentWork
    val selectedTool = viewModel.selectedTool
    val syncState = viewModel.syncState
    val needsReconciliation = syncState is SyncState.NeedsRecovery ||
        syncState is SyncState.Conflict || syncState is SyncState.InvalidRequest

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row {

            Button(onClick = { viewModel.selectTool(InkTool.PEN) }) {
                Text("Pen")
            }

            Button(onClick = { viewModel.selectTool(InkTool.ERASER) }) {
                Text("Eraser")
            }

            Button(onClick = { viewModel.clear() }) {
                Text("Clear")
            }

            Button(
                enabled = syncState !is SyncState.Syncing && !needsReconciliation,
                onClick = { viewModel.check() }
            ) {
                Text("Check")
            }
        }

        Text("Revision: ${studentWork.revision}")
        Text(
            when (val state = syncState) {
                SyncState.Idle -> "Not synchronized"
                SyncState.Syncing -> "Saving changes…"
                SyncState.Synced -> "Changes saved"
                is SyncState.NeedsRecovery ->
                    "Sync paused: server revision ${state.acceptedRevision}. Keep your drawing."
                SyncState.Conflict -> "Sync conflict. Your local changes are preserved."
                SyncState.InvalidRequest -> "Invalid sync request. Your drawing is preserved."
                SyncState.TemporaryFailure -> "Connection unavailable. Tap Check to retry."
                SyncState.UnexpectedFailure -> "Sync failed. Tap Check to retry."
            }
        )

        InkSurface(
            document = studentWork.document,
            selectedTool = selectedTool,

            onStrokesAdded = { newStrokes ->
                viewModel.addStrokes(newStrokes)
            },

            onStrokesRemoved = { strokesToRemove ->
                viewModel.removeStrokes(strokesToRemove)
            },

            modifier = Modifier.weight(1f)
        )
    }
}
