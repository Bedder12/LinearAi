package com.example.myapplication

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication.analysis.network.AnalysisNetwork
import com.example.myapplication.analysis.repository.AnalysisRepository
import com.example.myapplication.ink.InkSurface
import com.example.myapplication.ink.InkTool
import com.example.myapplication.ui.workspace.MathWorkspaceViewModel
import com.example.myapplication.ui.workspace.MathWorkspaceViewModelFactory

@Composable
fun MathWorkspaceScreen() {

    val viewModel: MathWorkspaceViewModel = viewModel(
        factory = MathWorkspaceViewModelFactory(
            analysisRepository = AnalysisRepository(
                api = AnalysisNetwork.api
            )
        )
    )

    val studentWork = viewModel.studentWork
    val selectedTool = viewModel.selectedTool

    Column(
        modifier = Modifier.fillMaxSize()
    ) {

        Row {

            Button(
                onClick = {
                    viewModel.selectTool(InkTool.PEN)
                }
            ) {
                Text("Pen")
            }

            Button(
                onClick = {
                    viewModel.selectTool(InkTool.ERASER)
                }
            ) {
                Text("Eraser")
            }

            Button(
                onClick = {
                    viewModel.clear()
                }
            ) {
                Text("Clear")
            }

            Button(
                onClick = {
                    viewModel.check()
                }
            ) {
                Text("Check")
            }
        }

        Text("Revision: ${studentWork.revision}")

        InkSurface(
            document = studentWork.document,
            selectedTool = selectedTool,

            onStrokesAdded = { newStrokes ->
                viewModel.addStrokes(newStrokes)
            },

            onStrokesRemoved = { strokesToRemove ->
                viewModel.removeStrokes(strokesToRemove)
            },

            modifier = Modifier.weight(1f)
        )
    }
}