package com.example.myapplication.Domain

data class PendingInkChange(
    val revision: Long,
    val change: InkChange
)