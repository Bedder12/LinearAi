package com.example.myapplication.ui.workspace

import com.example.myapplication.analysis.network.AnalysisApi
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.AnalysisResponsePayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MathWorkspaceViewModelTest {
    @Test
    fun newEditsDuringRequestRemainPending() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val response = CompletableDeferred<AnalysisResponsePayload>()
            val api = object : AnalysisApi {
                override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload =
                    response.await()
            }
            val viewModel = MathWorkspaceViewModel(AnalysisRepository(api))
            viewModel.clear() // revision 1
            viewModel.check()
            runCurrent()
            viewModel.clear() // revision 2, while revision 1 is in flight
            response.complete(AnalysisResponsePayload(1L, "accepted"))
            advanceUntilIdle()

            assertEquals(2L, viewModel.studentWork.revision)
            assertEquals(listOf(2L), viewModel.studentWork.pendingChanges.map { it.revision })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun repeatedCheckDoesNotStartParallelRequests() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val response = CompletableDeferred<AnalysisResponsePayload>()
            var requestCount = 0
            val api = object : AnalysisApi {
                override suspend fun check(request: AnalysisRequestPayload): AnalysisResponsePayload {
                    requestCount++
                    return response.await()
                }
            }
            val viewModel = MathWorkspaceViewModel(AnalysisRepository(api))
            viewModel.clear()
            viewModel.check()
            viewModel.check()
            runCurrent()
            assertEquals(1, requestCount)
            assertTrue(viewModel.syncState is SyncState.Syncing)
            response.complete(AnalysisResponsePayload(1L, "accepted"))
            advanceUntilIdle()
            assertEquals(1, requestCount)
            assertTrue(viewModel.studentWork.pendingChanges.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }
}
