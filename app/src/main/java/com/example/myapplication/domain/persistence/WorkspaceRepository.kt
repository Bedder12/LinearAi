package com.example.myapplication.domain.persistence

import androidx.ink.strokes.Stroke
import com.example.myapplication.domain.TrackedStroke

const val LEGACY_COORDINATE_PROFILE_ID = "legacy-raw-canvas-v1"
const val LEGACY_COORDINATE_PROFILE_VERSION = 1
const val LEGACY_COORDINATE_UNIT = "canvas-px"
const val WORKSPACE_FORMAT_VERSION = 1
const val PAGE_FORMAT_VERSION = 1

data class WorkspaceSnapshot(
    val documentId: String,
    val pageId: String,
    val workSessionId: String,
    val revision: Long,
    val strokes: List<TrackedStroke>,
    val documentFormatVersion: Int,
    val pageFormatVersion: Int,
    val coordinateProfileId: String,
    val coordinateProfileVersion: Int,
    val coordinateUnit: String
)

data class StoredOutboxEvent(
    val position: Int,
    val eventType: String,
    val payloadJson: String
)

data class OutboxBatchIdentity(
    val batchId: String,
    val workSessionId: String,
    val revision: Long,
    val eventCount: Int,
    val payloadSha256: String
)

data class StoredOutboxBatch(
    val identity: OutboxBatchIdentity,
    val events: List<StoredOutboxEvent>
)

interface WorkspaceRepository {
    suspend fun createOrLoadWorkspace(): WorkspaceSnapshot

    suspend fun loadWorkspace(): WorkspaceSnapshot?

    suspend fun addStrokes(strokes: Collection<Stroke>): WorkspaceSnapshot

    suspend fun addTrackedStrokes(strokes: Collection<TrackedStroke>): WorkspaceSnapshot

    suspend fun removeStrokes(strokes: Collection<TrackedStroke>): WorkspaceSnapshot

    suspend fun clear(): WorkspaceSnapshot

    suspend fun readPendingBatches(): List<StoredOutboxBatch>

    suspend fun acknowledgeBatches(batches: List<OutboxBatchIdentity>)
}

open class WorkspaceRepositoryException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)
