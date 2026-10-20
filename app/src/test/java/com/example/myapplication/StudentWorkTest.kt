package com.example.myapplication

import com.example.myapplication.domain.InkChange
import com.example.myapplication.domain.StudentWork
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test

class StudentWorkTest {

    @Test
    fun clear_incrementsRevision_andAddsClearChange() {
        val work = StudentWork(
            revision = 3L
        )

        val result = work.clear()

        assertEquals(4L, result.revision)
        assertTrue(result.document.strokes.isEmpty())
        assertEquals(1, result.pendingChanges.size)
        assertEquals(
            InkChange.DocumentCleared,
            result.pendingChanges.first().change
        )
        assertEquals(
            4L,
            result.pendingChanges.first().revision
        )
    }
}