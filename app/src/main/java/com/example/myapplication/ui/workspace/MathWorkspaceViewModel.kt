package com.example.myapplication.ui.workspace

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.ink.strokes.Stroke
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.domain.StudentWork
import com.example.myapplication.domain.TrackedStroke
import com.example.myapplication.ink.InkTool
import kotlinx.coroutines.launch

class MathWorkspaceViewModel(
    private val analysisRepository: AnalysisRepository
) : ViewModel() {

    var studentWork by mutableStateOf(StudentWork())
        private set

    var selectedTool by mutableStateOf(InkTool.PEN)
        private set

    fun selectTool(tool: InkTool) {
        selectedTool = tool
    }

    fun addStrokes(strokes: Collection<Stroke>) {
        studentWork = studentWork.addStrokes(strokes)
    }

    fun removeStrokes(strokes: Set<TrackedStroke>) {
        studentWork = studentWork.removeStrokes(strokes)
    }

    fun clear() {
        studentWork = studentWork.clear()
    }

    fun check() {
        viewModelScope.launch {
            val response = analysisRepository.analyze(studentWork)

            studentWork = studentWork.markChangesSynced(
                acceptedRevision = response.acceptedRevision
            )
        }
    }
}