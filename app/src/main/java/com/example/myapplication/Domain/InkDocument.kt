package com.example.myapplication.Domain

data class InkDocument(
    val strokes: List<TrackedStroke> = emptyList()
) {

    fun clear(): InkDocument {
        return copy(
            strokes = emptyList()
        )
    }

    fun removeStrokes(
        strokesToRemove: Set<TrackedStroke>
    ): InkDocument {
        return copy(
            strokes = strokes.filterNot { trackedStroke ->
                trackedStroke in strokesToRemove
            }
        )
    }
}