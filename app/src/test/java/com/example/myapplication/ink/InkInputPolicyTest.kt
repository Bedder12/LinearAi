package com.example.myapplication.ink

import androidx.compose.ui.input.pointer.PointerType
import org.junit.Assert.assertEquals
import org.junit.Test

class InkInputPolicyTest {
    @Test
    fun stylusAndFingerAreAllowedToDraw() {
        assertEquals(InkInputChecker.DRAW, InkInputPolicy.classify(PointerType.Stylus))
        assertEquals(InkInputChecker.DRAW, InkInputPolicy.classify(PointerType.Touch))
    }

    @Test
    fun mouseAndUnknownInputRemainIgnored() {
        assertEquals(InkInputChecker.IGNORE, InkInputPolicy.classify(PointerType.Mouse))
        assertEquals(InkInputChecker.IGNORE, InkInputPolicy.classify(PointerType.Unknown))
    }
}
