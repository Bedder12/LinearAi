package com.example.myapplication

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.domain.InkDocument
import com.example.myapplication.ink.InkSurface
import com.example.myapplication.ink.InkTool
import com.example.myapplication.ui.workspace.MathWorkspaceViewModel
import com.example.myapplication.ui.workspace.MathWorkspaceViewModelFactory
import com.example.myapplication.ui.workspace.RemoteSyncState
import com.example.myapplication.ui.workspace.WorkspaceUiState

@Composable
fun MathWorkspaceScreen(viewModelFactory: MathWorkspaceViewModelFactory) {
    val viewModel: MathWorkspaceViewModel = viewModel(factory = viewModelFactory)
    val workspaceState = viewModel.workspaceState
    val remoteSyncState = viewModel.remoteSyncState
    val snapshot = when (workspaceState) {
        WorkspaceUiState.Loading -> null
        is WorkspaceUiState.Ready -> workspaceState.snapshot
        is WorkspaceUiState.Saving -> workspaceState.snapshot
        is WorkspaceUiState.LocalSaveError -> workspaceState.snapshot
    }
    var pagesExpanded by rememberSaveable { mutableStateOf(true) }

    if (snapshot == null) {
        val errorState = workspaceState as? WorkspaceUiState.LocalSaveError
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    if (errorState == null) {
                        "Loading workspace…"
                    } else {
                        "Unable to load the local workspace. Existing data was not replaced."
                    }
                )
                if (errorState != null) {
                    Button(onClick = viewModel::retryLocalSave) {
                        Text("Retry loading")
                    }
                }
            }
        }
        return
    }

    val isSaveError = workspaceState is WorkspaceUiState.LocalSaveError
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WorkspaceToolSelector(
                selectedTool = viewModel.selectedTool,
                onToolSelected = viewModel::selectTool
            )
            Button(enabled = !isSaveError, onClick = viewModel::clear) { Text("Clear") }
            Button(enabled = false, onClick = {}) { Text("Check unavailable") }
        }

        Text("Revision: ${snapshot.revision}")
        Text(
            when (workspaceState) {
                WorkspaceUiState.Loading -> "Loading workspace…"
                is WorkspaceUiState.Ready -> "Saved on this device"
                is WorkspaceUiState.Saving -> "Saving locally…"
                is WorkspaceUiState.LocalSaveError ->
                    "Local save failed. The last committed drawing is shown."
            }
        )
        Text(remoteSyncMessage(remoteSyncState))
        if (workspaceState is WorkspaceUiState.LocalSaveError) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(workspaceState.message, modifier = Modifier.weight(1f))
                Button(onClick = viewModel::retryLocalSave) {
                    Text(if (workspaceState.snapshot == null) "Retry loading" else "Retry save")
                }
            }
        }

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
                        document = InkDocument(snapshot.strokes),
                        selectedTool = viewModel.selectedTool,
                        onStrokesAdded = viewModel::addStrokes,
                        onStrokesRemoved = viewModel::removeStrokes,
                        interactionEnabled = !isSaveError,
                        modifier = Modifier.weight(1f).testTag("ink-surface"),
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
                        document = InkDocument(snapshot.strokes),
                        selectedTool = viewModel.selectedTool,
                        onStrokesAdded = viewModel::addStrokes,
                        onStrokesRemoved = viewModel::removeStrokes,
                        interactionEnabled = !isSaveError,
                        modifier = Modifier.weight(1f).testTag("ink-surface"),
                    )
                }
            }
        }
    }
}

private fun remoteSyncMessage(state: RemoteSyncState): String =
    when (state) {
        RemoteSyncState.Checking -> "Checking local sync queue…"
        RemoteSyncState.NoPendingChanges ->
            "No pending remote changes. Remote synchronization is unavailable."
        is RemoteSyncState.Pending ->
            "Pending remote synchronization: ${state.batchCount} batch(es). Events remain on this device."
        RemoteSyncState.Unavailable ->
            "Remote sync status is unavailable. No sync was attempted."
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
