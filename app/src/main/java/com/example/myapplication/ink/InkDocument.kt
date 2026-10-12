package com.example.myapplication.ink

import androidx.ink.strokes.Stroke

data class InkDocument (
    val strokes: List<Stroke> = emptyList()
) {
    fun clear(): InkDocument {
        return copy(strokes = emptyList())
    }

    // Filter what's targeted to be removed from what should stay
    fun removeStrokes(strokesToRemove: Set<Stroke>): InkDocument {
        return copy(
            strokes = strokes.filterNot { stroke ->
                stroke in strokesToRemove
            }
        )
    }
}