package com.videochat.data.repository

import com.videochat.data.api.RetrofitClient
import com.videochat.data.model.*

class FriendRepository {
    private val api = RetrofitClient.apiService
    
    suspend fun searchUser(username: String): Result<User?> {
        return try {
            val response = api.searchUser(username)
            if (response.isSuccessful) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendFriendRequest(toUserId: Long): Result<Unit> {
        return try {
            val response = api.sendFriendRequest(SendFriendRequest(toUserId))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Request failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getFriendRequests(): Result<List<FriendRequest>> {
        return try {
            val response = api.getFriendRequests()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun acceptFriendRequest(requestId: Long): Result<Unit> {
        return try {
            val response = api.handleFriendRequest(requestId, FriendRequestResponse(true))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun rejectFriendRequest(requestId: Long): Result<Unit> {
        return try {
            val response = api.handleFriendRequest(requestId, FriendRequestResponse(false))
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun getFriendList(): Result<List<User>> {
        return try {
            val response = api.getFriendList()
            if (response.isSuccessful) Result.success(response.body() ?: emptyList())
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun deleteFriend(friendId: Long): Result<Unit> {
        return try {
            val response = api.deleteFriend(friendId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
