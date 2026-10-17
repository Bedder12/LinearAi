package com.example.myapplication.ink

import androidx.ink.strokes.Stroke

data class StudentWork(
    val document: InkDocument = InkDocument(),
    val revision: Long = 0L,
    val pendingChanges: List<PendingInkChange> = emptyList()
) {

    fun addStrokes(
        newStrokes: Collection<Stroke>
    ): StudentWork {

        val nextRevision = revision + 1

        val trackedStrokes = newStrokes.map { stroke ->
            TrackedStroke(
                stroke = stroke
            )
        }

        return copy(
            document = document.copy(
                strokes = document.strokes + trackedStrokes
            ),
            revision = nextRevision,
            pendingChanges = pendingChanges +
                    trackedStrokes.map { trackedStroke ->
                        PendingInkChange(
                            revision = nextRevision,
                            change = InkChange.StrokeAdded(
                                stroke = trackedStroke
                            )
                        )
                    }
        )
    }

    fun removeStrokes(
        strokesToRemove: Set<TrackedStroke>
    ): StudentWork {

        val nextRevision = revision + 1

        return copy(
            document = document.removeStrokes(strokesToRemove),
            revision = nextRevision,
            pendingChanges = pendingChanges +
                    strokesToRemove.map { trackedStroke ->
                        PendingInkChange(
                            revision = nextRevision,
                            change = InkChange.StrokeRemoved(
                                strokeId = trackedStroke.id
                            )
                        )
                    }
        )
    }

    fun clear(): StudentWork {

        val nextRevision = revision + 1

        return copy(
            document = document.clear(),
            revision = nextRevision,
            pendingChanges = pendingChanges +
                    PendingInkChange(
                        revision = nextRevision,
                        change = InkChange.DocumentCleared
                    )
        )
    }

    fun markChangesSynced(
        acceptedRevision: Long
    ): StudentWork {
        return copy(
            pendingChanges = pendingChanges.filter { pending ->
                pending.revision > acceptedRevision
            }
        )
    }
}