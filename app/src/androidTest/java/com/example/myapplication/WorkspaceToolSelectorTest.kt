package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.myapplication.ink.InkTool
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceToolSelectorTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun penIsInitiallySelected() {
        composeRule.setContent {
            WorkspaceToolSelector(selectedTool = InkTool.PEN, onToolSelected = {})
        }

        composeRule.onNodeWithText("Pen").assertIsSelected()
        composeRule.onNodeWithText("Eraser").assertIsNotSelected()
    }

    @Test
    fun clickingEraserUpdatesSelectedTool() {
        var selectedTool by mutableStateOf(InkTool.PEN)
        composeRule.setContent {
            WorkspaceToolSelector(
                selectedTool = selectedTool,
                onToolSelected = { selectedTool = it },
            )
        }

        composeRule.onNodeWithText("Eraser").performClick()
        composeRule.onNodeWithText("Eraser").assertIsSelected()
        composeRule.onNodeWithText("Pen").assertIsNotSelected()
    }
}
