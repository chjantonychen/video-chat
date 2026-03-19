package com.videochat.data.repository

import com.videochat.data.api.RetrofitClient
import com.videochat.data.model.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class MessageRepository {
    private val api = RetrofitClient.apiService
    private val baseUrl = "http://192.168.101.23:8080"
    
    suspend fun sendMessage(toUserId: Long, type: Int, content: String): Result<Message> {
        return try {
            val response = api.sendMessage(SendMessageRequest(toUserId, type, content))
            if (response.isSuccessful) {
                response.body()?.let { Result.success(it) }
                    ?: Result.failure(Exception("Send failed"))
            } else {
                Result.failure(Exception("Failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun uploadFile(file: File): Result<String> {
        return try {
            val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
            val response = api.uploadFile(part)
            if (response.isSuccessful) {
                response.body()?.let { 
                    // Return full URL
                    val fullUrl = if (it.url.startsWith("http")) it.url else "$baseUrl${it.url}"
                    Result.success(fullUrl)
                } ?: Result.failure(Exception("Upload failed"))
            } else {
                Result.failure(Exception("Upload failed: ${response.code()} ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun sendImageMessage(toUserId: Long, imageUrl: String): Result<Message> {
        return sendMessage(toUserId, MESSAGE_TYPE_IMAGE, imageUrl)
    }
    
    suspend fun sendVoiceMessage(toUserId: Long, voiceUrl: String): Result<Message> {
        return sendMessage(toUserId, MESSAGE_TYPE_VOICE, voiceUrl)
    }
    
    suspend fun getMessageList(friendId: Long, page: Int = 1): Result<List<Message>> {
        return try {
            val response = api.getMessageList(friendId, page)
            if (response.isSuccessful) {
                // Fix image URLs in messages
                val messages = response.body() ?: emptyList()
                val fixedMessages = messages.map { msg ->
                    if (msg.type == MESSAGE_TYPE_IMAGE && !msg.content.startsWith("http")) {
                        Message(
                            id = msg.id,
                            fromUserId = msg.fromUserId,
                            toUserId = msg.toUserId,
                            type = msg.type,
                            content = "$baseUrl${msg.content}",
                            isRead = msg.isRead,
                            createdAt = msg.createdAt
                        )
                    } else msg
                }
                Result.success(fixedMessages)
            } else {
                Result.failure(Exception("Failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun markAsRead(friendId: Long): Result<Unit> {
        return try {
            val response = api.markAsRead(friendId)
            if (response.isSuccessful) Result.success(Unit)
            else Result.failure(Exception("Failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
