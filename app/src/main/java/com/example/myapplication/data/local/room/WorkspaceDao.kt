package com.example.myapplication.data.local.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface WorkspaceDao {
    @Query("SELECT * FROM workspace_anchor WHERE id = 1")
    suspend fun getAnchor(): WorkspaceAnchorEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAnchor(anchor: WorkspaceAnchorEntity)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun countDocuments(): Int

    @Query("DELETE FROM workspace_anchor WHERE id = 1")
    suspend fun deleteAnchor(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDocument(document: DocumentEntity)

    @Query("SELECT * FROM documents WHERE documentId = :documentId")
    suspend fun getDocument(documentId: String): DocumentEntity?

    @Query(
        """
        UPDATE documents
        SET revision = :nextRevision
        WHERE documentId = :documentId AND revision = :expectedRevision
        """
    )
    suspend fun advanceRevision(
        documentId: String,
        expectedRevision: Long,
        nextRevision: Long
    ): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPage(page: PageEntity)

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position LIMIT 1")
    suspend fun getFirstPage(documentId: String): PageEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStrokes(strokes: List<StrokeEntity>)

    @Query("SELECT * FROM strokes WHERE pageId = :pageId ORDER BY position")
    suspend fun getStrokes(pageId: String): List<StrokeEntity>

    @Query(
        """
        SELECT strokeId FROM strokes
        WHERE pageId = :pageId AND strokeId IN (:strokeIds)
        ORDER BY position
        """
    )
    suspend fun getExistingStrokeIds(pageId: String, strokeIds: List<String>): List<String>

    @Query("DELETE FROM strokes WHERE pageId = :pageId AND strokeId IN (:strokeIds)")
    suspend fun deleteStrokes(pageId: String, strokeIds: List<String>)

    @Query("DELETE FROM strokes WHERE pageId = :pageId")
    suspend fun clearStrokes(pageId: String)

    @Query("SELECT MAX(position) FROM strokes WHERE pageId = :pageId")
    suspend fun getLastStrokePosition(pageId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxBatch(batch: OutboxBatchEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutboxEvents(events: List<OutboxEventEntity>)

    @Query("SELECT * FROM outbox_batches WHERE workSessionId = :workSessionId ORDER BY revision")
    suspend fun getOutboxBatches(workSessionId: String): List<OutboxBatchEntity>

    @Query(
        """
        SELECT * FROM outbox_events
        WHERE workSessionId = :workSessionId AND revision = :revision
        ORDER BY position
        """
    )
    suspend fun getOutboxEvents(workSessionId: String, revision: Long): List<OutboxEventEntity>

    @Query(
        """
        SELECT * FROM outbox_batches
        WHERE workSessionId = :workSessionId AND revision = :revision
        """
    )
    suspend fun getOutboxBatch(workSessionId: String, revision: Long): OutboxBatchEntity?

    @Query("DELETE FROM outbox_batches WHERE workSessionId = :workSessionId AND revision = :revision")
    suspend fun deleteOutboxBatch(workSessionId: String, revision: Long): Int

    @Query("SELECT COUNT(*) FROM outbox_events WHERE workSessionId = :workSessionId AND revision = :revision")
    suspend fun countOutboxEvents(workSessionId: String, revision: Long): Int

    @Query("SELECT * FROM strokes WHERE strokeId = :strokeId")
    suspend fun getStroke(strokeId: String): StrokeEntity?

    @Query("UPDATE strokes SET encodedStroke = :encodedStroke WHERE strokeId = :strokeId")
    suspend fun replaceEncodedStroke(strokeId: String, encodedStroke: ByteArray): Int
}
