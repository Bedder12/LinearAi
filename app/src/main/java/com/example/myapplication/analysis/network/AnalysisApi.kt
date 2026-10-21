package com.example.myapplication.analysis.network

import com.example.myapplication.analysis.transport.AnalysisRequestPayload
import com.example.myapplication.analysis.transport.AnalysisResponsePayload
import retrofit2.http.Body
import retrofit2.http.POST

interface AnalysisApi {
    @POST("analysis/check")
    suspend fun check(
        @Body request: AnalysisRequestPayload
    ): AnalysisResponsePayload
}