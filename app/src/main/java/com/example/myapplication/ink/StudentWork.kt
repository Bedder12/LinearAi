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

        return copy(
            document = document.copy(
                strokes = document.strokes + newStrokes
            ),
            revision = nextRevision,
            pendingChanges = pendingChanges +
                    newStrokes.map { stroke ->
                        PendingInkChange(
                            revision = nextRevision,
                            change = InkChange.StrokeAdded(stroke)
                        )
                    }
        )
    }

    fun removeStrokes(
        strokesToRemove: Set<Stroke>
    ): StudentWork {

        val nextRevision = revision + 1

        return copy(
            document = document.removeStrokes(strokesToRemove),
            revision = nextRevision,
            pendingChanges = pendingChanges +
                    strokesToRemove.map { stroke ->
                        PendingInkChange(
                            revision = nextRevision,
                            change = InkChange.StrokeRemoved(stroke)
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