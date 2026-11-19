package com.example.myapplication.data.local.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["workSessionId"], unique = true)]
)
internal data class DocumentEntity(
    @PrimaryKey val documentId: String,
    val workSessionId: String,
    val revision: Long,
    val formatVersion: Int,
    val coordinateProfileId: String,
    val coordinateProfileVersion: Int,
    val coordinateUnit: String
)

@Entity(
    tableName = "workspace_anchor",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["documentId"], unique = true)]
)
internal data class WorkspaceAnchorEntity(
    @PrimaryKey val id: Int = DEFAULT_ID,
    val documentId: String
) {
    companion object {
        const val DEFAULT_ID = 1
    }
}

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["documentId"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["documentId"]),
        Index(value = ["documentId", "position"], unique = true)
    ]
)
internal data class PageEntity(
    @PrimaryKey val pageId: String,
    val documentId: String,
    val position: Int,
    val formatVersion: Int,
    val coordinateProfileId: String,
    val coordinateProfileVersion: Int,
    val coordinateUnit: String
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["pageId"],
            childColumns = ["pageId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pageId"]),
        Index(value = ["pageId", "position"], unique = true)
    ]
)
internal data class StrokeEntity(
    @PrimaryKey val strokeId: String,
    val pageId: String,
    val position: Int,
    val codecVersion: Int,
    val encodedStroke: ByteArray
)

@Entity(
    tableName = "outbox_batches",
    primaryKeys = ["workSessionId", "revision"],
    indices = [Index(value = ["batchId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["workSessionId"],
            childColumns = ["workSessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
internal data class OutboxBatchEntity(
    val workSessionId: String,
    val revision: Long,
    val batchId: String,
    val eventCount: Int,
    val payloadSha256: String
)

@Entity(
    tableName = "outbox_events",
    primaryKeys = ["workSessionId", "revision", "position"],
    foreignKeys = [
        ForeignKey(
            entity = OutboxBatchEntity::class,
            parentColumns = ["workSessionId", "revision"],
            childColumns = ["workSessionId", "revision"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["workSessionId", "revision"])]
)
internal data class OutboxEventEntity(
    val workSessionId: String,
    val revision: Long,
    val position: Int,
    val eventType: String,
    val payloadJson: String
)
