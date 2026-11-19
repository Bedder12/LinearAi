package com.example.myapplication

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    var pagesExpanded by rememberSaveable { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceToolSelector(
                selectedTool = selectedTool,
                onToolSelected = viewModel::selectTool
            )
            Button(onClick = { viewModel.clear() }) { Text("Clear") }
            Button(
                enabled = syncState !is SyncState.Syncing && !needsReconciliation,
                onClick = { viewModel.check() }
            ) { Text("Check") }
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

        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val wideLayout = maxWidth >= 720.dp
            if (wideLayout) {
                Row(modifier = Modifier.fillMaxSize()) {
                    WorkspacePageSidebar(
                        expanded = pagesExpanded,
                        onToggle = { pagesExpanded = !pagesExpanded },
                        modifier = Modifier.width(if (pagesExpanded) 176.dp else 104.dp)
                    )
                    InkSurface(
                        document = studentWork.document,
                        selectedTool = selectedTool,
                        onStrokesAdded = viewModel::addStrokes,
                        onStrokesRemoved = viewModel::removeStrokes,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    WorkspacePageSidebar(
                        expanded = pagesExpanded,
                        onToggle = { pagesExpanded = !pagesExpanded },
                        compact = true,
                    )
                    InkSurface(
                        document = studentWork.document,
                        selectedTool = selectedTool,
                        onStrokesAdded = viewModel::addStrokes,
                        onStrokesRemoved = viewModel::removeStrokes,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Slice 01: stateless tool selector.
 * The ViewModel remains the source of truth for the active ink tool.
 */
@Composable
internal fun WorkspaceToolSelector(
    selectedTool: InkTool,
    onToolSelected: (InkTool) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 8.dp),
    ) {
        FilterChip(
            selected = selectedTool == InkTool.PEN,
            onClick = { onToolSelected(InkTool.PEN) },
            label = { Text("Pen") },
        )
        FilterChip(
            selected = selectedTool == InkTool.ERASER,
            onClick = { onToolSelected(InkTool.ERASER) },
            label = { Text("Eraser") },
        )
    }
}

/**
 * Slice 02: navigation shell for the existing single-page document.
 * This is intentionally NOT a multi-page model: Page 1 is a label, not a new page ID.
 */
@Composable
internal fun WorkspacePageSidebar(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.padding(8.dp)) {
            Button(onClick = onToggle) {
                Text(if (expanded) "Hide pages" else "Show pages")
            }
            if (expanded) {
                Text(
                    text = "Page 1",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(8.dp),
                )
                if (!compact) {
                    Text(
                        text = "Current document",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
