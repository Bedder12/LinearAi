package com.example.myapplication.ui.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.myapplication.analysis.repository.AnalysisRepository

class MathWorkspaceViewModelFactory(
    private val analysisRepository: AnalysisRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(MathWorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MathWorkspaceViewModel(
                analysisRepository = analysisRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class: ${modelClass.name}"
        )
    }
}