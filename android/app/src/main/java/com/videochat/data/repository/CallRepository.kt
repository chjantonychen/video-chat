package com.videochat.data.repository

import com.videochat.data.api.ApiService
import com.videochat.data.model.CallRecord

class CallRepository(
    private val apiService: ApiService
) {
    suspend fun getCallHistory(page: Int = 1, size: Int = 20): Result<List<CallRecord>> {
        return try {
            val response = apiService.getCallHistory(page, size)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to get call history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}