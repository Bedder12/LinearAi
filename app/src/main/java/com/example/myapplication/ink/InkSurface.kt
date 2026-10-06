package com.example.myapplication.ink

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

@Composable
fun InkSurface(
    document: InkDocument,
    onDocumentChange: (InkDocument) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderer = remember {
        CanvasStrokeRenderer.create()
    }

    val identityMatrix = remember {
        Matrix()
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
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
                document.strokes.forEach { stroke ->
                    renderer.draw(
                        canvas = drawContext.canvas.nativeCanvas,
                        stroke = stroke,
                        strokeToScreenTransform = identityMatrix
                    )
                }
            }

            InProgressStrokes(
                defaultBrush = InkBrushes.defaultPen,
                onStrokesFinished = { finishedStrokes ->

                    val updatedDocument = document.copy(
                        strokes = document.strokes + finishedStrokes
                    )

                    onDocumentChange(updatedDocument)
                }
            )
        }
    }
}