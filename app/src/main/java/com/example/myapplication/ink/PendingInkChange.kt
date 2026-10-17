package com.example.myapplication.ink

data class PendingInkChange(
    val revision: Long,
    val change: InkChange
)