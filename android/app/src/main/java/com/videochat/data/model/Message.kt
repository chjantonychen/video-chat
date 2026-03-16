package com.videochat.data.model

import com.google.gson.annotations.SerializedName

// Message types
const val MESSAGE_TYPE_TEXT = 1
const val MESSAGE_TYPE_IMAGE = 2
const val MESSAGE_TYPE_VOICE = 3

data class Message(
    val id: Long,
    @SerializedName("from_user_id") val fromUserId: Long,
    @SerializedName("to_user_id") val toUserId: Long,
    val type: Int,
    val content: String,
    @SerializedName("is_read") val isRead: Int,
    @SerializedName("created_at") val createdAt: String
)

data class SendMessageRequest(
    @SerializedName("toUserId") val toUserId: Long,
    val type: Int,
    val content: String
)

data class SendFriendRequest(
    @SerializedName("toUserId") val toUserId: Long
)

data class FriendRequestResponse(
    val accept: Boolean
)

data class FileUploadResponse(
    val url: String,
    val filename: String
)

data class CallRecord(
    val id: Long,
    @SerializedName("caller_id") val callerId: Long,
    @SerializedName("callee_id") val calleeId: Long,
    val type: Int,
    val status: Int,
    val duration: Int,
    @SerializedName("created_at") val createdAt: String
)
