package com.example.myapplication.domain

data class PendingInkChange(
    val revision: Long,
    val change: InkChange
)