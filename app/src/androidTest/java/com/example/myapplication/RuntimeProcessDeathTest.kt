package com.example.myapplication

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.room.WorkspaceRepositoryProvider
import com.example.myapplication.domain.persistence.StoredOutboxBatch
import com.example.myapplication.domain.persistence.WorkspaceSnapshot
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RuntimeProcessDeathTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = WorkspaceRepositoryProvider.create(context)
    private val preferences by lazy {
        context.getSharedPreferences(PREFERENCES_NAME, 0)
    }

    @Test
    fun test01SeedWorkspaceForForceStop() {
        waitForSavedWorkspace()
        val before = loadWorkspace()
        val initialStrokeIds = before.strokes.map { it.id }.toSet()

        drawStroke()
        val seeded = awaitWorkspace { workspace ->
            workspace.revision == before.revision + 1 &&
                workspace.strokes.size == before.strokes.size + 1
        }
        val addedStroke = seeded.strokes.single { it.id !in initialStrokeIds }
        val batches = readPendingBatches()

        preferences.edit()
            .putString(KEY_DOCUMENT_ID, seeded.documentId)
            .putString(KEY_PAGE_ID, seeded.pageId)
            .putString(KEY_WORK_SESSION_ID, seeded.workSessionId)
            .putLong(KEY_REVISION, seeded.revision)
            .putString(KEY_STROKE_IDS, seeded.strokes.joinToString("\n") { it.id })
            .putString(KEY_ADDED_STROKE_ID, addedStroke.id)
            .putString(KEY_OUTBOX_FINGERPRINT, fingerprint(batches))
            .apply()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Saved on this device")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun test02VerifyWorkspaceAfterForceStopAndRelaunch() {
        val expectedDocumentId = requireNotNull(preferences.getString(KEY_DOCUMENT_ID, null))
        val expectedPageId = requireNotNull(preferences.getString(KEY_PAGE_ID, null))
        val expectedWorkSessionId = requireNotNull(preferences.getString(KEY_WORK_SESSION_ID, null))
        val expectedStrokeIds = requireNotNull(preferences.getString(KEY_STROKE_IDS, null))
            .split('\n')
        val expectedAddedStrokeId = requireNotNull(preferences.getString(KEY_ADDED_STROKE_ID, null))
        val expectedRevision = preferences.getLong(KEY_REVISION, -1L)
        val expectedOutboxFingerprint =
            requireNotNull(preferences.getString(KEY_OUTBOX_FINGERPRINT, null))

        assertTrue(expectedRevision >= 0L)
        waitForSavedWorkspace()

        val restored = loadWorkspace()
        assertEquals(expectedDocumentId, restored.documentId)
        assertEquals(expectedPageId, restored.pageId)
        assertEquals(expectedWorkSessionId, restored.workSessionId)
        assertEquals(expectedRevision, restored.revision)
        assertEquals(expectedStrokeIds, restored.strokes.map { it.id })
        assertTrue(restored.strokes.any { it.id == expectedAddedStrokeId })
        assertEquals(expectedOutboxFingerprint, fingerprint(readPendingBatches()))
        assertTrue(countRenderedDarkPixels() > 0)
    }

    private fun waitForSavedWorkspace() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithText("Saved on this device")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun drawStroke() {
        composeRule.onNodeWithTag("ink-surface").performTouchInput {
            down(Offset(visibleSize.width * 0.2f, visibleSize.height * 0.55f))
            moveTo(Offset(visibleSize.width * 0.8f, visibleSize.height * 0.62f))
            up()
        }
    }

    private fun awaitWorkspace(predicate: (WorkspaceSnapshot) -> Boolean): WorkspaceSnapshot {
        composeRule.waitUntil(10_000) {
            runBlocking(Dispatchers.IO) {
                repository.loadWorkspace()?.let(predicate) ?: false
            }
        }
        return loadWorkspace()
    }

    private fun loadWorkspace(): WorkspaceSnapshot =
        runBlocking(Dispatchers.IO) { repository.loadWorkspace()!! }

    private fun readPendingBatches(): List<StoredOutboxBatch> =
        runBlocking(Dispatchers.IO) { repository.readPendingBatches() }

    private fun fingerprint(batches: List<StoredOutboxBatch>): String {
        val canonical = buildString {
            batches.forEach { batch ->
                append(batch.identity.batchId).append('|')
                append(batch.identity.workSessionId).append('|')
                append(batch.identity.revision).append('|')
                append(batch.identity.eventCount).append('|')
                append(batch.identity.payloadSha256).append('\n')
                batch.events.forEach { event ->
                    append(event.position).append('|')
                    append(event.eventType).append('|')
                    append(event.payloadJson.length).append('|')
                    append(event.payloadJson).append('\n')
                }
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun countRenderedDarkPixels(): Int {
        val pixels = composeRule.onNodeWithTag("ink-surface").captureToImage().toPixelMap()
        var count = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.alpha > 0.5f &&
                    color.red < 0.4f &&
                    color.green < 0.4f &&
                    color.blue < 0.4f
                ) {
                    count++
                }
            }
        }
        return count
    }

    private companion object {
        const val PREFERENCES_NAME = "s03b-process-death-test"
        const val KEY_DOCUMENT_ID = "documentId"
        const val KEY_PAGE_ID = "pageId"
        const val KEY_WORK_SESSION_ID = "workSessionId"
        const val KEY_REVISION = "revision"
        const val KEY_STROKE_IDS = "strokeIds"
        const val KEY_ADDED_STROKE_ID = "addedStrokeId"
        const val KEY_OUTBOX_FINGERPRINT = "outboxFingerprint"
    }
}
