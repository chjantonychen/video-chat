package com.videochat.data.repository

import com.videochat.data.api.RetrofitClient
import com.videochat.data.local.PreferencesManager
import com.videochat.data.model.*
import java.util.Base64
import org.json.JSONObject

class AuthRepository(private val preferencesManager: PreferencesManager) {
    
    private val api = RetrofitClient.apiService
    
    // 同步检查：只检查token是否存在（不进行网络验证）
    fun isLoggedInSync(): Boolean {
        return preferencesManager.getTokenBlocking() != null
    }
    
    // Decode user ID from JWT token
    private fun decodeUserIdFromToken(token: String): Long? {
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payload = String(Base64.getDecoder().decode(parts[1]))
                val json = JSONObject(payload)
                // sub is stored as string in JWT
                json.getString("sub").toLongOrNull()
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    suspend fun register(username: String, password: String, nickname: String?): Result<String> {
        return try {
            val response = api.register(RegisterRequest(username, password, nickname))
            if (response.isSuccessful) {
                val token = response.body()?.token ?: ""
                preferencesManager.saveToken(token)
                RetrofitClient.setToken(token)
                
                // Get user info from API
                try {
                    val userResponse = api.getMe()
                    if (userResponse.isSuccessful) {
                        userResponse.body()?.let { user ->
                            preferencesManager.saveUserInfo(user.id, user.username)
                        }
                    }
                } catch (e: Exception) {
                    // Try token parsing as fallback
                    decodeUserIdFromToken(token)?.let { userId ->
                        preferencesManager.saveUserInfo(userId, username)
                    }
                }
                
                Result.success(token)
            } else {
                Result.failure(Exception("Register failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun login(username: String, password: String): Result<String> {
        return try {
            val response = api.login(LoginRequest(username, password))
            if (response.isSuccessful) {
                val token = response.body()?.token ?: ""
                preferencesManager.saveToken(token)
                RetrofitClient.setToken(token)
                
                // Get user info from API
                try {
                    val userResponse = api.getMe()
                    if (userResponse.isSuccessful) {
                        userResponse.body()?.let { user ->
                            preferencesManager.saveUserInfo(user.id, user.username)
                        }
                    }
                } catch (e: Exception) {
                    // Try token parsing as fallback
                    decodeUserIdFromToken(token)?.let { userId ->
                        preferencesManager.saveUserInfo(userId, username)
                    }
                }
                
                Result.success(token)
            } else {
                Result.failure(Exception("Login failed: ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout() {
        android.util.Log.d("AuthRepository", "logout: clearing preferences")
        preferencesManager.clear()
        RetrofitClient.setToken(null)
        android.util.Log.d("AuthRepository", "logout: done")
    }
    
    suspend fun isLoggedIn(): Boolean {
        val token = preferencesManager.getTokenSync() ?: return false
        return try {
            RetrofitClient.setToken(token)
            val response = api.getUser(0)
            response.code() != 401 && response.code() != 403
        } catch (e: Exception) {
            false
        }
    }
}
