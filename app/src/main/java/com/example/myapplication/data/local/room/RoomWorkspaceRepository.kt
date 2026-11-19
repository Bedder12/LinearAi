package com.example.myapplication.data.local.room

import androidx.ink.strokes.Stroke
import androidx.room.withTransaction
import com.example.myapplication.analysis.transport.InkChangePayload
import com.example.myapplication.analysis.transport.toPayload
import com.example.myapplication.data.local.ink.LocalInkStrokeCodec
import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.domain.persistence.LEGACY_COORDINATE_PROFILE_ID
import com.example.myapplication.domain.persistence.LEGACY_COORDINATE_PROFILE_VERSION
import com.example.myapplication.domain.persistence.LEGACY_COORDINATE_UNIT
import com.example.myapplication.domain.persistence.PAGE_FORMAT_VERSION
import com.example.myapplication.domain.persistence.WORKSPACE_FORMAT_VERSION
import com.example.myapplication.domain.persistence.OutboxBatchIdentity
import com.example.myapplication.domain.persistence.StoredOutboxBatch
import com.example.myapplication.domain.persistence.StoredOutboxEvent
import com.example.myapplication.domain.persistence.WorkspaceRepository
import com.example.myapplication.domain.persistence.WorkspaceSnapshot
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class RoomWorkspaceRepository(
    private val database: LocalWorkspaceDatabase,
    private val strokeCodec: LocalInkStrokeCodec = LocalInkStrokeCodec()
) : WorkspaceRepository {
    private val dao = database.workspaceDao()
    private val v1Json = Json { classDiscriminator = "type" }

    override suspend fun createOrLoadWorkspace(): WorkspaceSnapshot =
        database.withTransaction {
            val existingAnchor = dao.getAnchor()
            if (existingAnchor != null) {
                return@withTransaction loadSnapshot(existingAnchor.documentId)
                    ?: throw WorkspacePersistenceException("Workspace anchor has no document")
            }
            if (dao.countDocuments() != 0) {
                throw WorkspacePersistenceException(
                    "Workspace anchor is missing from existing persisted data"
                )
            }

            val documentId = UUID.randomUUID().toString()
            val pageId = UUID.randomUUID().toString()
            val workSessionId = UUID.randomUUID().toString()
            dao.insertDocument(
                DocumentEntity(
                    documentId = documentId,
                    workSessionId = workSessionId,
                    revision = 0L,
                    formatVersion = WORKSPACE_FORMAT_VERSION,
                    coordinateProfileId = LEGACY_COORDINATE_PROFILE_ID,
                    coordinateProfileVersion = LEGACY_COORDINATE_PROFILE_VERSION,
                    coordinateUnit = LEGACY_COORDINATE_UNIT
                )
            )
            dao.insertPage(
                PageEntity(
                    pageId = pageId,
                    documentId = documentId,
                    position = 0,
                    formatVersion = PAGE_FORMAT_VERSION,
                    coordinateProfileId = LEGACY_COORDINATE_PROFILE_ID,
                    coordinateProfileVersion = LEGACY_COORDINATE_PROFILE_VERSION,
                    coordinateUnit = LEGACY_COORDINATE_UNIT
                )
            )
            dao.insertAnchor(WorkspaceAnchorEntity(documentId = documentId))
            loadSnapshot(documentId)
                ?: throw WorkspacePersistenceException("Created workspace could not be loaded")
        }

    override suspend fun loadWorkspace(): WorkspaceSnapshot? =
        database.withTransaction {
            dao.getAnchor()?.let { loadSnapshot(it.documentId) }
        }

    override suspend fun addStrokes(strokes: Collection<Stroke>): WorkspaceSnapshot {
        val trackedStrokes = strokes.map { TrackedStroke(stroke = it) }
        if (trackedStrokes.isEmpty()) return requireWorkspaceSnapshot()

        val encodedStrokes = trackedStrokes.map { tracked ->
            StoredStroke(
                trackedStroke = tracked,
                encoded = strokeCodec.encode(tracked),
                event = prepareEvent(InkChange.StrokeAdded(tracked))
            )
        }

        return database.withTransaction {
            val (document, page) = getWorkspaceRows()
            val lastPosition = dao.getLastStrokePosition(page.pageId) ?: -1
            val rows = encodedStrokes.mapIndexed { index, stored ->
                StrokeEntity(
                    strokeId = stored.trackedStroke.id,
                    pageId = page.pageId,
                    position = lastPosition + index + 1,
                    codecVersion = LocalInkStrokeCodec.VERSION,
                    encodedStroke = stored.encoded
                )
            }
            val nextRevision = document.revision + 1
            advanceRevision(document, nextRevision)
            dao.insertStrokes(rows)
            appendOutboxBatch(
                workSessionId = document.workSessionId,
                revision = nextRevision,
                preparedEvents = encodedStrokes.map(StoredStroke::event)
            )
            loadSnapshot(document.documentId)
                ?: throw WorkspacePersistenceException("Workspace disappeared during stroke add")
        }
    }

    override suspend fun removeStrokes(strokes: Collection<TrackedStroke>): WorkspaceSnapshot {
        val requestedIds = strokes.map(TrackedStroke::id).distinct()
        return database.withTransaction {
            val (document, page) = getWorkspaceRows()
            if (requestedIds.isEmpty()) return@withTransaction loadSnapshot(document.documentId)!!

            val existingIds = dao.getExistingStrokeIds(page.pageId, requestedIds)
            if (existingIds.isEmpty()) return@withTransaction loadSnapshot(document.documentId)!!

            val events = existingIds.map { strokeId ->
                prepareEvent(InkChange.StrokeRemoved(strokeId))
            }
            val nextRevision = document.revision + 1
            advanceRevision(document, nextRevision)
            dao.deleteStrokes(page.pageId, existingIds)
            appendOutboxBatch(document.workSessionId, nextRevision, events)
            loadSnapshot(document.documentId)
                ?: throw WorkspacePersistenceException("Workspace disappeared during stroke removal")
        }
    }

    override suspend fun clear(): WorkspaceSnapshot =
        database.withTransaction {
            val (document, page) = getWorkspaceRows()
            val nextRevision = document.revision + 1
            advanceRevision(document, nextRevision)
            dao.clearStrokes(page.pageId)
            appendOutboxBatch(
                workSessionId = document.workSessionId,
                revision = nextRevision,
                preparedEvents = listOf(prepareEvent(InkChange.DocumentCleared))
            )
            loadSnapshot(document.documentId)
                ?: throw WorkspacePersistenceException("Workspace disappeared during clear")
        }

    override suspend fun readPendingBatches(): List<StoredOutboxBatch> =
        database.withTransaction {
            val anchor = dao.getAnchor() ?: return@withTransaction emptyList()
            val document = dao.getDocument(anchor.documentId)
                ?: throw WorkspacePersistenceException("Workspace anchor has no document")
            dao.getOutboxBatches(document.workSessionId).map { batch ->
                val events = dao.getOutboxEvents(batch.workSessionId, batch.revision)
                validateBatch(batch, events)
                StoredOutboxBatch(
                    identity = batch.toIdentity(),
                    events = events.map {
                        StoredOutboxEvent(
                            position = it.position,
                            eventType = it.eventType,
                            payloadJson = it.payloadJson
                        )
                    }
                )
            }
        }

    override suspend fun acknowledgeBatches(batches: List<OutboxBatchIdentity>) {
        if (batches.isEmpty()) return
        require(batches.distinct().size == batches.size) {
            "Acknowledgment contains duplicate batch identities"
        }
        database.withTransaction {
            batches.forEach { identity ->
                val batch = dao.getOutboxBatch(identity.workSessionId, identity.revision)
                    ?: throw InvalidOutboxAcknowledgmentException("Acknowledged batch no longer exists")
                val events = dao.getOutboxEvents(identity.workSessionId, identity.revision)
                validateBatch(batch, events)
                if (batch.toIdentity() != identity) {
                    throw InvalidOutboxAcknowledgmentException(
                        "Acknowledgment does not match the committed batch snapshot"
                    )
                }
                if (dao.deleteOutboxBatch(identity.workSessionId, identity.revision) != 1) {
                    throw InvalidOutboxAcknowledgmentException("Committed batch could not be acknowledged")
                }
            }
        }
    }

    private suspend fun requireWorkspaceSnapshot(): WorkspaceSnapshot =
        loadWorkspace() ?: throw WorkspacePersistenceException("Workspace has not been created")

    private suspend fun getWorkspaceRows(): Pair<DocumentEntity, PageEntity> {
        val anchor = dao.getAnchor()
            ?: throw WorkspacePersistenceException("Workspace has not been created")
        val document = dao.getDocument(anchor.documentId)
            ?: throw WorkspacePersistenceException("Workspace anchor has no document")
        val page = dao.getFirstPage(document.documentId)
            ?: throw WorkspacePersistenceException("Workspace document has no page")
        return document to page
    }

    private suspend fun loadSnapshot(documentId: String): WorkspaceSnapshot? {
        val document = dao.getDocument(documentId) ?: return null
        val page = dao.getFirstPage(documentId)
            ?: throw WorkspacePersistenceException("Workspace document has no page")
        val strokes = dao.getStrokes(page.pageId).map { row ->
            strokeCodec.decode(row.strokeId, row.codecVersion, row.encodedStroke)
        }
        return WorkspaceSnapshot(
            documentId = document.documentId,
            pageId = page.pageId,
            workSessionId = document.workSessionId,
            revision = document.revision,
            strokes = strokes,
            documentFormatVersion = document.formatVersion,
            pageFormatVersion = page.formatVersion,
            coordinateProfileId = page.coordinateProfileId,
            coordinateProfileVersion = page.coordinateProfileVersion,
            coordinateUnit = page.coordinateUnit
        )
    }

    private suspend fun advanceRevision(document: DocumentEntity, nextRevision: Long) {
        val updated = dao.advanceRevision(
            documentId = document.documentId,
            expectedRevision = document.revision,
            nextRevision = nextRevision
        )
        if (updated != 1) {
            throw WorkspaceConcurrencyException("Workspace revision changed during mutation")
        }
    }

    private suspend fun appendOutboxBatch(
        workSessionId: String,
        revision: Long,
        preparedEvents: List<PreparedEvent>
    ) {
        check(preparedEvents.isNotEmpty()) { "An outbox revision must contain at least one event" }
        val eventRows = preparedEvents.mapIndexed { position, event ->
            OutboxEventEntity(
                workSessionId = workSessionId,
                revision = revision,
                position = position,
                eventType = event.type,
                payloadJson = event.payloadJson
            )
        }
        val digest = payloadDigest(eventRows)
        dao.insertOutboxBatch(
            OutboxBatchEntity(
                workSessionId = workSessionId,
                revision = revision,
                batchId = UUID.randomUUID().toString(),
                eventCount = eventRows.size,
                payloadSha256 = digest
            )
        )
        dao.insertOutboxEvents(eventRows)
    }

    private fun prepareEvent(change: InkChange): PreparedEvent {
        val payload = change.toPayload()
        val type = when (payload) {
            is InkChangePayload.StrokeAdded -> "stroke_added"
            is InkChangePayload.StrokeRemoved -> "stroke_removed"
            InkChangePayload.DocumentCleared -> "document_cleared"
        }
        return PreparedEvent(
            type = type,
            payloadJson = v1Json.encodeToString<InkChangePayload>(payload)
        )
    }

    private fun validateBatch(batch: OutboxBatchEntity, events: List<OutboxEventEntity>) {
        if (batch.eventCount <= 0 ||
            events.size != batch.eventCount ||
            events.indices.any { events[it].position != it } ||
            payloadDigest(events) != batch.payloadSha256
        ) {
            throw OutboxIntegrityException(
                "Outbox revision ${batch.revision} is incomplete or has changed payload data"
            )
        }
    }

    private fun OutboxBatchEntity.toIdentity() =
        OutboxBatchIdentity(
            batchId = batchId,
            workSessionId = workSessionId,
            revision = revision,
            eventCount = eventCount,
            payloadSha256 = payloadSha256
        )

    private fun payloadDigest(events: List<OutboxEventEntity>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        events.forEach { event ->
            digest.update(event.position.toString().toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(event.eventType.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(event.payloadJson.toByteArray(StandardCharsets.UTF_8))
            digest.update(0xFF.toByte())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class PreparedEvent(
        val type: String,
        val payloadJson: String
    )

    private data class StoredStroke(
        val trackedStroke: TrackedStroke,
        val encoded: ByteArray,
        val event: PreparedEvent
    )
}

class WorkspacePersistenceException(message: String) : IllegalStateException(message)

class WorkspaceConcurrencyException(message: String) : IllegalStateException(message)

class OutboxIntegrityException(message: String) : IllegalStateException(message)

class InvalidOutboxAcknowledgmentException(message: String) : IllegalArgumentException(message)
