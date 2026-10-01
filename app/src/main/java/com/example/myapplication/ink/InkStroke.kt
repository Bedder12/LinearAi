package com.example.myapplication.ink

data class InkStroke (
    val points: MutableList<InkPoint> = mutableListOf(),
    val tool: InkTool = InkTool.PEN,
    val width: Float = 4f,
    val colorArgb: Long = 0xFF000000
    )