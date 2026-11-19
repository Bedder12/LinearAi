package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.myapplication.data.local.room.WorkspaceRepositoryProvider
import com.example.myapplication.ui.workspace.MathWorkspaceViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModelFactory = MathWorkspaceViewModelFactory(
            workspaceRepository = WorkspaceRepositoryProvider.create(applicationContext)
        )
        setContent {
            MathWorkspaceScreen(viewModelFactory)
        }
    }
}