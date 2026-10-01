package com.example.myapplication

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.myapplication.ink.InkDocument
import com.example.myapplication.ink.InkSurface

@Composable
fun MathWorkspaceScreen() {
    var document by remember { mutableStateOf(InkDocument()) }

    InkSurface(
        document = document,
        onDocumentChange = { updatedDocument ->
            document = updatedDocument
        },
        modifier = Modifier.fillMaxSize()
    )
}