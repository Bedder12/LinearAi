package com.example.myapplication.ink

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

@Composable
fun InkSurface(
    document: InkDocument,
    selectedTool: InkTool,
    onStrokesAdded: (Collection<Stroke>) -> Unit,
    onStrokesRemoved: (Set<TrackedStroke>) -> Unit,
    modifier: Modifier = Modifier
) {

    val currentDocument by rememberUpdatedState(document)

    val renderer = remember {
        CanvasStrokeRenderer.create()
    }

    val identityMatrix = remember {
        Matrix()
    }

    val hitTester = remember {
        StrokeHitTester()
    }

    Box(
        modifier = modifier.pointerInput(selectedTool) {

            awaitPointerEventScope {

                while (true) {

                    val event = awaitPointerEvent(
                        PointerEventPass.Initial
                    )

                    event.changes.forEach { change ->

                        val decision =
                            InkInputPolicy.classify(change.type)

                        if (decision == InkInputChecker.IGNORE) {

                            change.consume()

                        } else if (
                            selectedTool == InkTool.ERASER &&
                            change.pressed
                        ) {

                            val hits = hitTester.findHits(
                                strokes = currentDocument.strokes,
                                x = change.position.x,
                                y = change.position.y,
                                radius = 20f
                            )

                            if (hits.isNotEmpty()) {
                                onStrokesRemoved(hits)
                            }

                            change.consume()
                        }
                    }
                }
            }
        }
    ) {

        Box(
            modifier = Modifier.fillMaxSize()
        ) {

            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {

                document.strokes.forEach { trackedStroke ->
                    renderer.draw(
                        canvas = drawContext.canvas.nativeCanvas,
                        stroke = trackedStroke.stroke,
                        strokeToScreenTransform = identityMatrix
                    )
                }
            }

            if (selectedTool == InkTool.PEN) {

                InProgressStrokes(
                    defaultBrush = InkBrushes.defaultPen,
                    onStrokesFinished = { finishedStrokes ->
                        onStrokesAdded(finishedStrokes)
                    }
                )
            }
        }
    }
}