package com.example.myapplication.ink

import androidx.ink.strokes.Stroke

data class InkDocument (
    val strokes: List<Stroke> = emptyList()
)