package com.example.myapplication.ink

import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes

object InkBrushes {
    val defaultPen: Brush = Brush(
        family = StockBrushes.pressurePen(),
        size = 4f,
        epsilon = 0.1f
    )
}