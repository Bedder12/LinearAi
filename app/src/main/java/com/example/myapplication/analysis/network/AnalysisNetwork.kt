package com.example.myapplication.analysis.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AnalysisNetwork {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/")
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        ).build()

     val api: AnalysisApi = retrofit.create(AnalysisApi::class.java)
}