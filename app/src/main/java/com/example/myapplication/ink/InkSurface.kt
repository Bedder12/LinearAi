package com.example.myapplication.ink

import android.graphics.Matrix
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
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
        modifier = modifier
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

        Box(modifier = Modifier.fillMaxSize()) {
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