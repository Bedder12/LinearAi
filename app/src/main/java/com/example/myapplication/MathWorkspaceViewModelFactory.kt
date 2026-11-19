package com.example.myapplication.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.domain.persistence.WorkspaceRepository

class MathWorkspaceViewModelFactory(
    private val workspaceRepository: WorkspaceRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(MathWorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MathWorkspaceViewModel(
                workspaceRepository = workspaceRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}