package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myapplication.ink.InkDocument
import com.example.myapplication.ink.InkSurface
import com.example.myapplication.ink.InkTool

@Composable
fun MathWorkspaceScreen() {

    var document by remember {
        mutableStateOf(InkDocument())
    }

    var selectedTool by remember {
        mutableStateOf(InkTool.PEN)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row {

            Button(
                onClick = {
                    selectedTool = InkTool.PEN
                }
            ) {
                Text("Pen")
            }

            Button(
                onClick = {
                    selectedTool = InkTool.ERASER
                }
            ) {
                Text("Eraser")
            }

            Button(
                onClick = {
                    document = document.clear()
                }
            ) {
                Text("Clear")
            }
        }

        InkSurface(
            document = document,
            selectedTool = selectedTool,
            onDocumentChange = { updatedDocument ->
                document = updatedDocument
            },
            modifier = Modifier.weight(1f)
        )
    }
}