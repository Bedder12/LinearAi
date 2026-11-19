package com.example.myapplication.ui.workspace

import androidx.ink.strokes.Stroke
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.domain.persistence.OutboxBatchIdentity
import com.example.myapplication.domain.persistence.StoredOutboxBatch
import com.example.myapplication.domain.persistence.StoredOutboxEvent
import com.example.myapplication.domain.persistence.WorkspaceRepository
import com.example.myapplication.domain.persistence.WorkspaceRepositoryException
import com.example.myapplication.domain.persistence.WorkspaceSnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MathWorkspaceViewModelTest {
    @Test
    fun loadsRepositoryStateBeforeExposingReadyWorkspace() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeWorkspaceRepository()
            val viewModel = MathWorkspaceViewModel(repository)

            assertEquals(WorkspaceUiState.Loading, viewModel.workspaceState)
            advanceUntilIdle()

            val ready = viewModel.workspaceState as WorkspaceUiState.Ready
            assertEquals(repository.snapshot, ready.snapshot)
            assertEquals(RemoteSyncState.NoPendingChanges, viewModel.remoteSyncState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun loadFailureIsVisibleAndRetryDoesNotCreateFallbackState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeWorkspaceRepository().apply { failNextLoad = true }
            val viewModel = MathWorkspaceViewModel(repository)
            advanceUntilIdle()

            val failed = viewModel.workspaceState as WorkspaceUiState.LocalSaveError
            assertEquals(null, failed.snapshot)
            assertEquals(1, repository.createCount)

            viewModel.retryLocalSave()
            advanceUntilIdle()

            assertTrue(viewModel.workspaceState is WorkspaceUiState.Ready)
            assertEquals(2, repository.createCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rapidEditsAreSerializedInCallbackOrder() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeWorkspaceRepository()
            val viewModel = MathWorkspaceViewModel(repository)
            advanceUntilIdle()

            viewModel.clear()
            viewModel.clear()
            viewModel.clear()
            runCurrent()

            assertTrue(viewModel.workspaceState is WorkspaceUiState.Saving)
            advanceUntilIdle()

            val ready = viewModel.workspaceState as WorkspaceUiState.Ready
            assertEquals(3L, ready.snapshot.revision)
            assertTrue(ready.snapshot.strokes.isEmpty())
            assertEquals(listOf("clear", "clear", "clear"), repository.operations)
            assertEquals(1, repository.maxConcurrentMutations)
            assertEquals(RemoteSyncState.Pending(3), viewModel.remoteSyncState)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun failedSaveRemainsQueuedUntilTheUserRetriesTheLocalSave() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeWorkspaceRepository().apply { failNextClear = true }
            val viewModel = MathWorkspaceViewModel(repository)
            advanceUntilIdle()

            viewModel.clear()
            advanceUntilIdle()

            val failed = viewModel.workspaceState as WorkspaceUiState.LocalSaveError
            assertEquals(0L, failed.snapshot?.revision)
            assertEquals(1, failed.queuedEditCount)
            assertEquals(0L, repository.snapshot.revision)

            viewModel.retryLocalSave()
            advanceUntilIdle()

            val ready = viewModel.workspaceState as WorkspaceUiState.Ready
            assertEquals(1L, ready.snapshot.revision)
            assertEquals(listOf("clear", "clear"), repository.operations)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun pendingOutboxIsReportedAsUnsyncedAndNeverAcknowledgedByCheckUi() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeWorkspaceRepository()
            val viewModel = MathWorkspaceViewModel(repository)
            advanceUntilIdle()

            viewModel.clear()
            advanceUntilIdle()

            assertEquals(RemoteSyncState.Pending(1), viewModel.remoteSyncState)
            assertEquals(1, repository.readPendingBatches().size)
            assertEquals(0, repository.acknowledgmentCount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        var snapshot = WorkspaceSnapshot(
            documentId = "document",
            pageId = "page",
            workSessionId = "session",
            revision = 0L,
            strokes = emptyList(),
            documentFormatVersion = 1,
            pageFormatVersion = 1,
            coordinateProfileId = "legacy-raw-canvas-v1",
            coordinateProfileVersion = 1,
            coordinateUnit = "canvas-px"
        )
        val operations = mutableListOf<String>()
        private val batches = mutableListOf<StoredOutboxBatch>()
        var failNextLoad = false
        var failNextClear = false
        var createCount = 0
        var acknowledgmentCount = 0
        var maxConcurrentMutations = 0
            private set
        private var activeMutations = 0

        override suspend fun createOrLoadWorkspace(): WorkspaceSnapshot {
            createCount++
            if (failNextLoad) {
                failNextLoad = false
                throw WorkspaceRepositoryException("database is unavailable")
            }
            return snapshot
        }

        override suspend fun loadWorkspace(): WorkspaceSnapshot = snapshot

        override suspend fun addStrokes(strokes: Collection<Stroke>): WorkspaceSnapshot =
            addTrackedStrokes(strokes.map { TrackedStroke(stroke = it) })

        override suspend fun addTrackedStrokes(
            strokes: Collection<TrackedStroke>
        ): WorkspaceSnapshot = mutate("add") {
            snapshot = snapshot.copy(
                revision = snapshot.revision + 1,
                strokes = snapshot.strokes + strokes
            )
            appendBatch("stroke_added")
            snapshot
        }

        override suspend fun removeStrokes(
            strokes: Collection<TrackedStroke>
        ): WorkspaceSnapshot = mutate("remove") {
            val ids = strokes.map { it.id }.toSet()
            val retained = snapshot.strokes.filterNot { it.id in ids }
            if (retained.size != snapshot.strokes.size) {
                snapshot = snapshot.copy(
                    revision = snapshot.revision + 1,
                    strokes = retained
                )
                appendBatch("stroke_removed")
            }
            snapshot
        }

        override suspend fun clear(): WorkspaceSnapshot = mutate("clear") {
            if (failNextClear) {
                failNextClear = false
                throw WorkspaceRepositoryException("disk write failed")
            }
            snapshot = snapshot.copy(revision = snapshot.revision + 1, strokes = emptyList())
            appendBatch("document_cleared")
            snapshot
        }

        override suspend fun readPendingBatches(): List<StoredOutboxBatch> = batches.toList()

        override suspend fun acknowledgeBatches(batches: List<OutboxBatchIdentity>) {
            acknowledgmentCount++
        }

        private suspend fun mutate(
            operation: String,
            block: suspend () -> WorkspaceSnapshot
        ): WorkspaceSnapshot {
            activeMutations++
            maxConcurrentMutations = maxOf(maxConcurrentMutations, activeMutations)
            try {
                delay(1)
                operations.add(operation)
                return block()
            } finally {
                activeMutations--
            }
        }

        private fun appendBatch(type: String) {
            val revision = snapshot.revision
            batches.add(
                StoredOutboxBatch(
                    identity = OutboxBatchIdentity(
                        batchId = "batch-$revision",
                        workSessionId = snapshot.workSessionId,
                        revision = revision,
                        eventCount = 1,
                        payloadSha256 = "digest-$revision"
                    ),
                    events = listOf(
                        StoredOutboxEvent(
                            position = 0,
                            eventType = type,
                            payloadJson = "{}"
                        )
                    )
                )
            )
        }
    }
}
