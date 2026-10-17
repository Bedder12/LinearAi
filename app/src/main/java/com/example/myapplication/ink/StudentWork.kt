package com.example.myapplication.ink

import androidx.ink.strokes.Stroke

data class StudentWork(
    val document: InkDocument = InkDocument(),
    val revision: Long = 0L,
    val pendingChange: List<InkChange> = emptyList()
) {

    fun addStrokes(
        newStrokes: Collection<Stroke>
    ): StudentWork {
        return copy(
            document = document.copy(
                strokes = document.strokes + newStrokes
            ),
            revision = revision + 1,
            pendingChange = pendingChange +
                    newStrokes.map { stroke ->
                        InkChange.StrokeAdded(stroke)
                    }
        )
    }

    fun removeStrokes(
        strokesToRemove: Set<Stroke>
    ): StudentWork {
        return copy(
            document = document.removeStrokes(strokesToRemove),
            revision = revision + 1,
            pendingChange = pendingChange +
                    strokesToRemove.map { stroke ->
                        InkChange.StrokeRemoved(stroke)
                    }
        )
    }

    fun clear(): StudentWork {
        return copy(
            document = document.clear(),
            revision = revision + 1,
            pendingChange =
                pendingChange + InkChange.DocumentCleared
        )
    }
}