package com.example.myapplication.analysis.repository

import com.example.myapplication.analysis.network.AnalysisApi
import com.example.myapplication.analysis.transport.AnalysisResponsePayload
import com.example.myapplication.analysis.transport.toAnalysisRequestPayload
import com.example.myapplication.domain.StudentWork

class AnalysisRepository (private val api: AnalysisApi)
{
    suspend fun analyze(
        studentWork: StudentWork
    ): AnalysisResponsePayload {
        val request = studentWork.toAnalysisRequestPayload()
        return api.check(request)
    }
}