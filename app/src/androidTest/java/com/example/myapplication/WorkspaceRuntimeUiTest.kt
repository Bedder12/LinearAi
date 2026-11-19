package com.example.myapplication

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.room.LocalWorkspaceDatabase
import com.example.myapplication.data.local.room.RoomWorkspaceRepository
import com.example.myapplication.domain.persistence.WorkspaceSnapshot
import com.example.myapplication.ink.InkBrushes
import com.example.myapplication.ink.StrokeHitTester
import com.example.myapplication.ui.workspace.MathWorkspaceViewModelFactory
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceRuntimeUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "runtime-ui-${UUID.randomUUID()}.db"
    private lateinit var database: LocalWorkspaceDatabase
    private lateinit var repository: RoomWorkspaceRepository
    private lateinit var factory: MathWorkspaceViewModelFactory

    @Before
    fun setUp() {
        database = LocalWorkspaceDatabase.openForTest(context, databaseName)
        repository = RoomWorkspaceRepository(database)
        factory = MathWorkspaceViewModelFactory(repository)
        runBlocking(Dispatchers.IO) { repository.createOrLoadWorkspace() }
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun drawingErasingClearingAndCheckAvailabilityUseDurableWorkspaceState() {
        composeRule.setContent {
            MathWorkspaceScreen(factory)
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Saved on this device")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Saved on this device").assertExists()
        composeRule.onNodeWithText("Check unavailable").assertIsNotEnabled()

        val initial = loadSnapshot()
        drawStroke()
        val added = awaitSnapshot { it.revision == initial.revision + 1 }
        assertEquals(1, added.strokes.size)
        val addedStrokeId = added.strokes.single().id
        composeRule.onNodeWithText("Saved on this device").assertExists()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(
                "Pending remote synchronization: 1 batch(es). Events remain on this device."
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Pending remote synchronization: 1 batch(es). Events remain on this device.")
            .assertExists()

        composeRule.onNodeWithText("Eraser").performClick()
        composeRule.onNodeWithText("Eraser").assertIsSelected()
        val input = added.strokes.single().stroke.inputs[0]
        assertEquals(
            1,
            StrokeHitTester().findHits(
                strokes = added.strokes,
                x = input.x,
                y = input.y,
                radius = 20f
            ).size
        )
        composeRule.onNodeWithTag("ink-surface").performTouchInput {
            down(Offset(input.x, input.y))
            up()
        }
        val erased = awaitSnapshot {
            it.revision == added.revision + 1 && it.strokes.isEmpty()
        }
        assertTrue(erased.strokes.none { it.id == addedStrokeId })

        composeRule.onNodeWithText("Pen").performClick()
        drawStroke(yFraction = 0.7f)
        val secondStroke = awaitSnapshot {
            it.revision == erased.revision + 1 && it.strokes.size == 1
        }
        composeRule.onNodeWithText("Clear").performClick()
        val cleared = awaitSnapshot {
            it.revision == secondStroke.revision + 1 && it.strokes.isEmpty()
        }
        assertEquals(initial.documentId, cleared.documentId)
        assertEquals(initial.pageId, cleared.pageId)
        assertEquals(initial.workSessionId, cleared.workSessionId)
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(
                "Pending remote synchronization: 4 batch(es). Events remain on this device."
            ).fetchSemanticsNodes().isNotEmpty()
        }

        val batches = runBlocking(Dispatchers.IO) { repository.readPendingBatches() }
        assertEquals(
            listOf("stroke_added", "stroke_removed", "stroke_added", "document_cleared"),
            batches.flatMap { it.events }.map { it.eventType }
        )
        assertEquals(cleared.revision, batches.last().identity.revision)
        assertEquals(InputToolType.TOUCH, added.strokes.first().stroke.inputs.getToolType())
    }

    @Test
    fun databaseFailureKeepsEditPendingUntilRetryCommitsIt() {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_outbox_event
            BEFORE INSERT ON outbox_events
            BEGIN
                SELECT RAISE(ABORT, 'injected outbox failure');
            END
            """.trimIndent()
        )
        composeRule.setContent { MathWorkspaceScreen(factory) }
        waitForSaved()

        drawStroke()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(
                "Local save failed. The last committed drawing is shown."
            ).fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(0L, loadSnapshot().revision)
        assertTrue(loadSnapshot().strokes.isEmpty())
        assertTrue(runBlocking(Dispatchers.IO) { repository.readPendingBatches() }.isEmpty())

        database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_outbox_event")
        composeRule.onNodeWithText("Retry save").performClick()
        val saved = awaitSnapshot { it.revision == 1L && it.strokes.size == 1 }
        assertEquals(1L, saved.revision)
        composeRule.onNodeWithText("Saved on this device").assertExists()
    }

    @Test
    fun loadFailureDoesNotReplacePersistedDocumentWithAnEmptyWorkspace() {
        val existing = runBlocking(Dispatchers.IO) {
            repository.addStrokes(listOf(testStroke()))
        }
        val dao = database.workspaceDao()
        assertEquals(1, runBlocking(Dispatchers.IO) { dao.deleteAnchor() })
        val brokenRepository = RoomWorkspaceRepository(database)
        val brokenFactory = MathWorkspaceViewModelFactory(brokenRepository)

        composeRule.setContent { MathWorkspaceScreen(brokenFactory) }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(
                "Unable to load the local workspace. Existing data was not replaced."
            ).fetchSemanticsNodes().isNotEmpty()
        }

        assertEquals(1, runBlocking(Dispatchers.IO) { dao.countDocuments() })
        assertEquals(
            existing.strokes.map { it.id },
            runBlocking(Dispatchers.IO) { dao.getStrokes(existing.pageId) }.map { it.strokeId }
        )
    }

    private fun waitForSaved() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Saved on this device")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun drawStroke(yFraction: Float = 0.4f) {
        composeRule.onNodeWithTag("ink-surface").performTouchInput {
            down(Offset(visibleSize.width * 0.2f, visibleSize.height * yFraction))
            moveTo(
                Offset(
                    visibleSize.width * 0.8f,
                    visibleSize.height * (yFraction + 0.08f)
                )
            )
            up()
        }
    }

    private fun loadSnapshot(): WorkspaceSnapshot =
        runBlocking(Dispatchers.IO) { repository.loadWorkspace()!! }

    private fun testStroke(): Stroke =
        Stroke(
            brush = InkBrushes.defaultPen,
            inputs = MutableStrokeInputBatch().apply {
                add(
                    type = InputToolType.STYLUS,
                    x = 20f,
                    y = 30f,
                    elapsedTimeMillis = 0L,
                    pressure = 0.5f
                )
                add(
                    type = InputToolType.STYLUS,
                    x = 30f,
                    y = 40f,
                    elapsedTimeMillis = 10L,
                    pressure = 0.6f
                )
            }
        )

    private fun awaitSnapshot(predicate: (WorkspaceSnapshot) -> Boolean): WorkspaceSnapshot {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runBlocking(Dispatchers.IO) {
                repository.loadWorkspace()?.let(predicate) ?: false
            }
        }
        return loadSnapshot()
    }

}
