package com.example.myapplication.analysis.repository

import com.example.myapplication.analysis.network.AnalysisApi
import com.example.myapplication.analysis.transport.toAnalysisRequestPayload
import com.example.myapplication.domain.StudentWork
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException

@Serializable
private data class ProblemDetails(
    val type: String,
    val acceptedRevision: Long? = null
)

class AnalysisRepository(private val api: AnalysisApi) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun analyze(studentWork: StudentWork): SyncResult {
        // Snapshot the outgoing payload: edits made while the request is in flight
        // must not alter its revision history or retry semantics.
        val request = studentWork.toAnalysisRequestPayload()
        return try {
            val response = api.check(request)
            if (response.status == "accepted" &&
                response.acceptedRevision in 0..request.revision
            ) {
                SyncResult.Success(response.acceptedRevision)
            } else {
                SyncResult.UnexpectedFailure
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: HttpException) {
            mapHttpError(exception)
        } catch (exception: IOException) {
            SyncResult.TemporaryFailure
        }
    }

    private fun mapHttpError(exception: HttpException): SyncResult {
        val status = exception.code()
        val problem = try {
            exception.response()?.errorBody()?.string()?.let {
                json.decodeFromString<ProblemDetails>(it)
            }
        } catch (_: Exception) {
            null
        }

        return when {
            status == 409 &&
                problem?.type == "/problems/revision-gap" &&
                problem.acceptedRevision != null &&
                problem.acceptedRevision >= 0 ->
                SyncResult.RevisionGap(problem.acceptedRevision)

            status == 409 && problem?.type == "/problems/revision-conflict" ->
                SyncResult.RevisionConflict

            status == 422 -> SyncResult.InvalidRequest
            status == 429 || status in 500..599 -> SyncResult.TemporaryFailure
            else -> SyncResult.UnexpectedFailure
        }
    }
}
