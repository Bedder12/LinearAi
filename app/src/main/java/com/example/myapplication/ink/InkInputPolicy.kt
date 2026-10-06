package com.example.myapplication.ink

import androidx.compose.ui.input.pointer.PointerType

object InkInputPolicy {

    fun classify(
        pointerType: PointerType
    ): InkInputChecker {

        return when (pointerType) {
            PointerType.Stylus -> InkInputChecker.DRAW
            else -> InkInputChecker.IGNORE
        }
    }
}