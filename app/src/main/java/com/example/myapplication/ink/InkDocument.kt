package com.example.myapplication.ink

data class InkDocument (
    val strokes: MutableList<InkStroke> = mutableListOf()
)