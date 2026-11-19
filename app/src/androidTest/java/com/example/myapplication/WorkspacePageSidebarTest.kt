package com.example.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspacePageSidebarTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun canCollapseAndReopenPageNavigation() {
        var expanded by mutableStateOf(true)
        composeRule.setContent {
            WorkspacePageSidebar(
                expanded = expanded,
                onToggle = { expanded = !expanded },
            )
        }

        composeRule.onNodeWithText("Page 1").assertExists()
        composeRule.onNodeWithText("Hide pages").performClick()
        composeRule.onNodeWithText("Page 1").assertDoesNotExist()
        composeRule.onNodeWithText("Show pages").performClick()
        composeRule.onNodeWithText("Page 1").assertExists()
    }

    @Test
    fun compactModeStillShowsCurrentPage() {
        composeRule.setContent {
            WorkspacePageSidebar(
                expanded = true,
                onToggle = {},
                compact = true,
            )
        }
        composeRule.onNodeWithText("Page 1").assertExists()
        composeRule.onNodeWithText("Current document").assertDoesNotExist()
    }
}
