package com.videochat.data.model

import com.google.gson.annotations.SerializedName

// WebSocket信令消息基类
sealed class CallSignal {
    data class CallInvite(
        @SerializedName("callId")
        val callId: Long,
        @SerializedName("fromUserId")
        val fromUserId: Long,
        @SerializedName("callType")
        val callType: Int
    ) : CallSignal()

    data class CallResponse(
        @SerializedName("callId")
        val callId: Long,
        @SerializedName("accept")
        val accept: Boolean
    ) : CallSignal()

    data class SdpOffer(
        @SerializedName("sdp")
        val sdp: String,
        @SerializedName("fromUserId")
        val fromUserId: Long
    ) : CallSignal()

    data class SdpAnswer(
        @SerializedName("sdp")
        val sdp: String,
        @SerializedName("fromUserId")
        val fromUserId: Long
    ) : CallSignal()

    data class IceCandidate(
        @SerializedName("candidate")
        val candidate: String,
        @SerializedName("sdpMid")
        val sdpMid: String?,
        @SerializedName("sdpMidIndex")
        val sdpMidIndex: Int?,
        @SerializedName("fromUserId")
        val fromUserId: Long
    ) : CallSignal()

    data class CallEnd(
        @SerializedName("callId")
        val callId: Long
    ) : CallSignal()
}

// 解析信令消息类型
data class SignalMessage(
    val type: String,
    val callId: Long? = null,
    val fromUserId: Long? = null,
    val callType: Int? = null,
    val accept: Boolean? = null,
    val sdp: String? = null,
    val candidate: String? = null,
    val sdpMid: String? = null,
    val sdpMidIndex: Int? = null
)