package com.videochat.data.model

import com.google.gson.annotations.SerializedName

data class User(
    val id: Long,
    val username: String,
    val nickname: String?,
    val avatar: String?,
    val signature: String?
)

data class LoginRequest(
    val username: String,
    val password: String
)

data class RegisterRequest(
    val username: String,
    val password: String,
    val nickname: String?
)

data class AuthResponse(
    val token: String
)

data class UpdateUserRequest(
    val nickname: String?,
    val avatar: String?,
    val signature: String?
)

data class FriendRequest(
    val id: Long,
    val fromUserId: Long,
    val fromUsername: String,
    val fromNickname: String?,
    val fromAvatar: String?
)
