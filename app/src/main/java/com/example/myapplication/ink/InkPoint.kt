package com.example.myapplication.ink

data class InkPoint(
    val x: Float,
    val y: Float,
    val timeStampMs: Long,
    val pressure: Float,
    val tilt: Float? = null,
    val orientation: Float? = null
)