package com.example.myapplication.domain

import junit.framework.TestCase
import org.junit.Test

class StudentWorkTest {

    @Test
    fun clear_incrementsRevision_addsClearChange_andPreservesWorkSessionId() {
        val work = StudentWork(
            workSessionId = "550e8400-e29b-41d4-a716-446655440000",
            revision = 3L
        )

        val result = work.clear()

        TestCase.assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            result.workSessionId
        )
        TestCase.assertEquals(4L, result.revision)
        TestCase.assertTrue(result.document.strokes.isEmpty())
        TestCase.assertEquals(1, result.pendingChanges.size)
        TestCase.assertEquals(
            InkChange.DocumentCleared,
            result.pendingChanges.first().change
        )
        TestCase.assertEquals(
            4L,
            result.pendingChanges.first().revision
        )
    }

    @Test
    fun markChangesSynced_removesAcceptedChangesAndKeepsNewerOnes() {

        val work = StudentWork(
            workSessionId = "550e8400-e29b-41d4-a716-446655440000",
            revision = 3L,
            pendingChanges = listOf(
                PendingInkChange(
                    revision = 1L,
                    change = InkChange.DocumentCleared
                ),
                PendingInkChange(
                    revision = 2L,
                    change = InkChange.DocumentCleared
                ),
                PendingInkChange(
                    revision = 3L,
                    change = InkChange.DocumentCleared
                )
            )
        )

        val result = work.markChangesSynced(
            acceptedRevision = 2L
        )

        TestCase.assertEquals(
            "550e8400-e29b-41d4-a716-446655440000",
            result.workSessionId
        )
        TestCase.assertEquals(1, result.pendingChanges.size)
        TestCase.assertEquals(
            3L,
            result.pendingChanges.first().revision
        )
    }
}
