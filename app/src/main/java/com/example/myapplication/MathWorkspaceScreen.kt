package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myapplication.ink.InkSurface
import com.example.myapplication.ink.InkTool
import com.example.myapplication.ink.StudentWork

@Composable
fun MathWorkspaceScreen() {

    var studentWork by remember {
        mutableStateOf(StudentWork())
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
                    studentWork = studentWork.clear()
                }
            ) {
                Text("Clear")
            }
        }

        Text("Revision: ${studentWork.revision}")

        InkSurface(
            document = studentWork.document,
            selectedTool = selectedTool,
            onDocumentChange = { updatedDocument ->
                studentWork = studentWork.updateDocument(updatedDocument)
            },
            modifier = Modifier.weight(1f)
        )
    }
}