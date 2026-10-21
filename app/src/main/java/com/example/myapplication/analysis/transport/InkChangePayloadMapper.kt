package com.example.myapplication.analysis.transport

import com.example.myapplication.domain.InkChange

fun InkChange.toPayload(): InkChangePayload{
    return when(this){
        is InkChange.StrokeAdded -> {
            InkChangePayload.StrokeAdded(
                stroke = stroke.toPayload()
            )
        }
        is InkChange.StrokeRemoved -> {
            InkChangePayload.StrokeRemoved(
                strokeId = strokeId
            )
        }
        is InkChange.DocumentCleared -> {
            InkChangePayload.DocumentCleared
        }
    }
}