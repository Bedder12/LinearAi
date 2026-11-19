package com.example.myapplication.data.local.room

import android.database.sqlite.SQLiteException
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.data.local.ink.CorruptInkStrokeException
import com.example.myapplication.data.local.ink.LocalInkStrokeCodec
import com.example.myapplication.data.local.ink.UnsupportedInkBrushFamilyException
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.domain.persistence.LEGACY_COORDINATE_PROFILE_ID
import com.example.myapplication.ink.InkBrushes
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomWorkspaceRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "workspace-test-${UUID.randomUUID()}.db"
    private lateinit var database: LocalWorkspaceDatabase
    private lateinit var repository: RoomWorkspaceRepository

    @Before
    fun setUp() {
        runBlocking {
            openDatabase()
            repository.createOrLoadWorkspace()
        }
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun workspaceIdsAndLegacyCoordinateProfileSurviveDatabaseReopen() = runBlocking {
        val initial = repository.loadWorkspace()!!
        assertEquals(LEGACY_COORDINATE_PROFILE_ID, initial.coordinateProfileId)
        assertEquals("canvas-px", initial.coordinateUnit)
        assertEquals(1, initial.documentFormatVersion)
        assertEquals(1, initial.pageFormatVersion)

        closeAndReopen()
        val restored = repository.createOrLoadWorkspace()

        assertEquals(initial.documentId, restored.documentId)
        assertEquals(initial.pageId, restored.pageId)
        assertEquals(initial.workSessionId, restored.workSessionId)
        assertEquals(initial.revision, restored.revision)
    }

    @Test
    fun codecRoundTripsStableIdBrushAndVariedInputAttributes() {
        val original = trackedStroke(
            id = "codec-stylus",
            offset = 2f,
            toolType = InputToolType.STYLUS,
            brush = Brush.createWithColorIntArgb(
                family = StockBrushes.pressurePen(StockBrushes.PressurePenVersion.V1),
                colorIntArgb = 0xFF336699.toInt(),
                size = 6.25f,
                epsilon = 0.075f
            )
        )
        val codec = LocalInkStrokeCodec()

        val restored = codec.decode(
            expectedStrokeId = original.id,
            codecVersion = LocalInkStrokeCodec.VERSION,
            encoded = codec.encode(original)
        )

        assertEquals(original.id, restored.id)
        assertEquals(original.stroke.brush.family, restored.stroke.brush.family)
        assertEquals(original.stroke.brush.colorLong, restored.stroke.brush.colorLong)
        assertEquals(original.stroke.brush.size, restored.stroke.brush.size, 0f)
        assertEquals(original.stroke.brush.epsilon, restored.stroke.brush.epsilon, 0f)
        assertInputBatchEquals(original.stroke, restored.stroke)
    }

    @Test
    fun codecRejectsUnsupportedBrushFamiliesAndCorruptRecords() {
        val input = inputs(0f, InputToolType.STYLUS)
        val unsupported = TrackedStroke(
            id = "unsupported-family",
            stroke = Stroke(
                brush = Brush(
                    family = StockBrushes.marker(StockBrushes.MarkerVersion.V1),
                    size = 4f,
                    epsilon = 0.1f
                ),
                inputs = input
            )
        )
        val codec = LocalInkStrokeCodec()

        assertThrows(UnsupportedInkBrushFamilyException::class.java) {
            codec.encode(unsupported)
        }

        val encoded = codec.encode(trackedStroke("valid", 0f))
        val truncated = encoded.copyOf(encoded.size - 1)
        assertThrows(CorruptInkStrokeException::class.java) {
            codec.decode("valid", LocalInkStrokeCodec.VERSION, truncated)
        }
        assertThrows(CorruptInkStrokeException::class.java) {
            codec.decode("different-id", LocalInkStrokeCodec.VERSION, encoded)
        }
    }

    @Test
    fun emptyDocumentHasNoStrokesOrOutbox() = runBlocking {
        val snapshot = repository.loadWorkspace()!!

        assertTrue(snapshot.strokes.isEmpty())
        assertTrue(repository.readPendingBatches().isEmpty())
        assertEquals(0L, snapshot.revision)
    }

    @Test
    fun multipleStrokesCommitAsOneOrderedCompleteRevisionBatch() = runBlocking {
        val snapshot = repository.addStrokes(
            listOf(
                trackedStrokeInput(1f, InputToolType.STYLUS),
                trackedStrokeInput(50f, InputToolType.TOUCH),
                trackedStrokeInput(100f, InputToolType.STYLUS)
            )
        )
        val batch = repository.readPendingBatches().single()

        assertEquals(1L, snapshot.revision)
        assertEquals(3, snapshot.strokes.size)
        assertEquals(1L, batch.identity.revision)
        assertEquals(3, batch.identity.eventCount)
        assertEquals(listOf(0, 1, 2), batch.events.map { it.position })
        assertEquals(listOf("stroke_added", "stroke_added", "stroke_added"), batch.events.map { it.eventType })
        assertEquals(
            snapshot.strokes.map { it.id },
            batch.events.map { Json.parseToJsonElement(it.payloadJson)
                .jsonObject.getValue("stroke").jsonObject.getValue("id").jsonPrimitive.content }
        )
    }

    @Test
    fun removalEventsUseDocumentOrderEvenWhenRequestOrderDiffers() = runBlocking {
        val added = repository.addStrokes(
            listOf(
                trackedStrokeInput(1f, InputToolType.STYLUS),
                trackedStrokeInput(50f, InputToolType.TOUCH),
                trackedStrokeInput(100f, InputToolType.STYLUS)
            )
        )

        repository.removeStrokes(added.strokes.reversed())

        val removalBatch = repository.readPendingBatches().last()
        assertEquals(2L, removalBatch.identity.revision)
        assertEquals(3, removalBatch.identity.eventCount)
        assertEquals(
            added.strokes.map { it.id },
            removalBatch.events.map {
                Json.parseToJsonElement(it.payloadJson)
                    .jsonObject.getValue("strokeId").jsonPrimitive.content
            }
        )
    }

    @Test
    fun emptyAddAndRemoveAndForeignStrokeDoNotAdvanceRevision() = runBlocking {
        val initial = repository.loadWorkspace()!!
        val afterEmptyAdd = repository.addStrokes(emptyList())
        val afterEmptyRemove = repository.removeStrokes(emptyList())
        val foreignStroke = trackedStroke("not-in-workspace", 0f)
        val afterForeignRemove = repository.removeStrokes(listOf(foreignStroke))

        assertEquals(initial.revision, afterEmptyAdd.revision)
        assertEquals(initial.revision, afterEmptyRemove.revision)
        assertEquals(initial.revision, afterForeignRemove.revision)
        assertTrue(repository.readPendingBatches().isEmpty())
    }

    @Test
    fun clearOnEmptyPreservesExistingRevisionAndEventContract() = runBlocking {
        val cleared = repository.clear()
        val batch = repository.readPendingBatches().single()

        assertEquals(1L, cleared.revision)
        assertTrue(cleared.strokes.isEmpty())
        assertEquals(1, batch.identity.eventCount)
        assertEquals("document_cleared", batch.events.single().eventType)
    }

    @Test
    fun databaseRollbackKeepsContentRevisionAndOutboxAtomic() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_outbox_event
            BEFORE INSERT ON outbox_events
            BEGIN
                SELECT RAISE(ABORT, 'injected outbox failure');
            END
            """.trimIndent()
        )

        assertThrows(SQLiteException::class.java) {
            runBlocking {
                repository.addStrokes(listOf(trackedStrokeInput(1f, InputToolType.STYLUS)))
            }
        }

        val snapshot = repository.loadWorkspace()!!
        assertEquals(0L, snapshot.revision)
        assertTrue(snapshot.strokes.isEmpty())
        assertTrue(repository.readPendingBatches().isEmpty())
    }

    @Test
    fun concurrentMutationsCreateUniqueOrderedRevisions() = runBlocking {
        coroutineScope {
            (0 until 8).map { index ->
                async(Dispatchers.IO) {
                    repository.addStrokes(
                        listOf(trackedStrokeInput(index * 20f, InputToolType.STYLUS))
                    )
                }
            }.awaitAll()
        }

        val snapshot = repository.loadWorkspace()!!
        val batches = repository.readPendingBatches()

        assertEquals(8L, snapshot.revision)
        assertEquals(8, snapshot.strokes.size)
        assertEquals((1L..8L).toList(), batches.map { it.identity.revision })
        assertTrue(batches.all { it.identity.eventCount == 1 })
    }

    @Test
    fun pendingEventsAndExactPayloadsSurviveRepositoryRecreation() = runBlocking {
        repository.addStrokes(
            listOf(
                trackedStrokeInput(1f, InputToolType.STYLUS),
                trackedStrokeInput(50f, InputToolType.TOUCH)
            )
        )
        val beforeWorkspace = repository.loadWorkspace()!!
        val before = repository.readPendingBatches()

        closeAndReopen()
        val afterWorkspace = repository.loadWorkspace()!!
        val after = repository.readPendingBatches()

        assertEquals(beforeWorkspace.documentId, afterWorkspace.documentId)
        assertEquals(beforeWorkspace.pageId, afterWorkspace.pageId)
        assertEquals(beforeWorkspace.workSessionId, afterWorkspace.workSessionId)
        assertEquals(beforeWorkspace.revision, afterWorkspace.revision)
        assertEquals(beforeWorkspace.strokes.map { it.id }, afterWorkspace.strokes.map { it.id })
        beforeWorkspace.strokes.zip(afterWorkspace.strokes).forEach { (expected, actual) ->
            assertEquals(expected.stroke.brush.family, actual.stroke.brush.family)
            assertEquals(expected.stroke.brush.colorLong, actual.stroke.brush.colorLong)
            assertEquals(expected.stroke.brush.size, actual.stroke.brush.size, 0f)
            assertEquals(expected.stroke.brush.epsilon, actual.stroke.brush.epsilon, 0f)
            assertInputBatchEquals(expected.stroke, actual.stroke)
        }
        assertEquals(before, after)
        assertEquals(before.single().identity, after.single().identity)
    }

    @Test
    fun oldOutboxPayloadIsImmutableAfterLaterDocumentMutation() = runBlocking {
        repository.addStrokes(listOf(trackedStrokeInput(1f, InputToolType.STYLUS)))
        val original = repository.readPendingBatches().single()
        repository.clear()
        val afterClear = repository.readPendingBatches()
            .single { it.identity.revision == original.identity.revision }

        assertEquals(original.identity, afterClear.identity)
        assertEquals(original.events, afterClear.events)
    }

    @Test
    fun acknowledgmentRemovesOnlyIdentifiedSentBatchAndPreservesNewerEvents() = runBlocking {
        repository.addStrokes(listOf(trackedStrokeInput(1f, InputToolType.STYLUS)))
        val sent = repository.readPendingBatches().single()
        repository.addStrokes(listOf(trackedStrokeInput(50f, InputToolType.TOUCH)))

        repository.acknowledgeBatches(listOf(sent.identity))

        val pending = repository.readPendingBatches()
        assertEquals(1, pending.size)
        assertEquals(2L, pending.single().identity.revision)
    }

    @Test
    fun invalidAcknowledgmentDoesNotDeleteAnyBatch() = runBlocking {
        repository.addStrokes(listOf(trackedStrokeInput(1f, InputToolType.STYLUS)))
        repository.addStrokes(listOf(trackedStrokeInput(50f, InputToolType.TOUCH)))
        val batches = repository.readPendingBatches()
        val invalidSecond = batches[1].identity.copy(payloadSha256 = "wrong-digest")

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.acknowledgeBatches(listOf(batches[0].identity, invalidSecond))
            }
        }

        assertEquals(batches, repository.readPendingBatches())
    }

    @Test
    fun corruptPersistedStrokeFailsExplicitlyWithoutDeletingDocument() = runBlocking {
        val snapshot = repository.addStrokes(
            listOf(trackedStrokeInput(1f, InputToolType.STYLUS))
        )
        val strokeId = snapshot.strokes.single().id
        database.workspaceDao().replaceEncodedStroke(strokeId, byteArrayOf(1, 2, 3))

        assertThrows(CorruptInkStrokeException::class.java) {
            runBlocking { repository.loadWorkspace() }
        }

        assertNotNull(database.workspaceDao().getAnchor())
        assertNotNull(database.workspaceDao().getStroke(strokeId))
        assertEquals(1L, database.workspaceDao()
            .getDocument(snapshot.documentId)!!.revision)
        assertEquals(1, repository.readPendingBatches().size)
    }

    @Test
    fun missingWorkspaceAnchorDoesNotCreateAnEmptyReplacement() = runBlocking {
        val snapshot = repository.addStrokes(
            listOf(trackedStrokeInput(1f, InputToolType.STYLUS))
        )
        val dao = database.workspaceDao()
        assertEquals(1, dao.deleteAnchor())

        assertThrows(WorkspacePersistenceException::class.java) {
            runBlocking { repository.createOrLoadWorkspace() }
        }

        assertEquals(1, dao.countDocuments())
        assertNotNull(dao.getDocument(snapshot.documentId))
        assertEquals(
            snapshot.strokes.map { it.id },
            dao.getStrokes(snapshot.pageId).map { it.strokeId }
        )
    }

    @Test
    fun schemaVersionIsExportedAndDatabaseUsesVersionOne() = runBlocking {
        val schemaPath =
            "com.example.myapplication.data.local.room.LocalWorkspaceDatabase/1.json"
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val schema = testContext.assets.open(schemaPath).bufferedReader().use { it.readText() }
        val databaseVersion = JSONObject(schema).getJSONObject("database").getInt("version")

        assertEquals(LocalWorkspaceDatabase.VERSION, databaseVersion)
        assertEquals(LocalWorkspaceDatabase.VERSION, database.openHelper.writableDatabase.version)
        assertTrue(schema.contains("\"tableName\": \"outbox_batches\""))
        assertTrue(schema.contains("\"tableName\": \"outbox_events\""))
    }

    private fun openDatabase() {
        database = LocalWorkspaceDatabase.openForTest(context, databaseName)
        repository = RoomWorkspaceRepository(database)
    }

    private suspend fun closeAndReopen() {
        database.close()
        openDatabase()
    }

    private fun trackedStrokeInput(
        offset: Float,
        toolType: InputToolType
    ): Stroke = stroke(offset, toolType)

    private fun trackedStroke(
        id: String,
        offset: Float,
        toolType: InputToolType = InputToolType.STYLUS,
        brush: Brush = InkBrushes.defaultPen
    ): TrackedStroke = TrackedStroke(
        id = id,
        stroke = stroke(offset, toolType, brush)
    )

    private fun stroke(
        offset: Float,
        toolType: InputToolType,
        brush: Brush = InkBrushes.defaultPen
    ): Stroke = Stroke(
        brush = brush,
        inputs = inputs(offset, toolType)
    )

    private fun inputs(offset: Float, toolType: InputToolType) =
        MutableStrokeInputBatch().apply {
            add(
                type = toolType,
                x = 10f + offset,
                y = 20f + offset,
                elapsedTimeMillis = 0L,
                pressure = 0.25f,
                tiltRadians = 0.1f,
                orientationRadians = 0.2f
            )
            add(
                type = toolType,
                x = 18f + offset,
                y = 31f + offset,
                elapsedTimeMillis = 17L,
                pressure = 0.75f,
                tiltRadians = 0.35f,
                orientationRadians = 0.55f
            )
        }

    private fun assertInputBatchEquals(expected: Stroke, actual: Stroke) {
        assertEquals(expected.inputs.size, actual.inputs.size)
        assertEquals(expected.inputs.getToolType(), actual.inputs.getToolType())
        for (index in 0 until expected.inputs.size) {
            val expectedInput = expected.inputs[index]
            val actualInput = actual.inputs[index]
            assertEquals(expectedInput.x, actualInput.x, 0.1f)
            assertEquals(expectedInput.y, actualInput.y, 0.1f)
            assertEquals(expectedInput.elapsedTimeMillis, actualInput.elapsedTimeMillis)
            assertEquals(expectedInput.pressure, actualInput.pressure, 0.05f)
            assertEquals(expectedInput.tiltRadians, actualInput.tiltRadians, 0.05f)
            assertEquals(expectedInput.orientationRadians, actualInput.orientationRadians, 0.05f)
        }
    }
}
