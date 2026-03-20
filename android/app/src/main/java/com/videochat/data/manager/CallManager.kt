package com.videochat.data.manager

import android.app.Application
import android.util.Log
import com.videochat.data.model.CallSignal
import com.videochat.data.websocket.CallWebSocketListener
import com.videochat.data.websocket.WebSocketService

class CallManager private constructor(private val application: Application) {

    companion object {
        private const val TAG = "CallManager"
        private var instance: CallManager? = null

        fun getInstance(application: Application): CallManager {
            if (instance == null) {
                instance = CallManager(application)
            }
            return instance!!
        }
    }

    private var webSocketService: WebSocketService? = null
    private var isConnected = false

    // 保存当前呼叫信息
    private var pendingCallToUserId: Long? = null
    private var pendingCallIsVideo: Boolean = false
    // 标记是否正在通话中（防止挂断后再次触发）
    private var isInCall: Boolean = false
    
    // 【新增】标记是否应该忽略来电（当CallScreen打开时）
    private var shouldIgnoreIncoming: Boolean = false

    // 回调接口
    var callListener: CallListener? = null

    interface CallListener {
        fun onIncomingCall(callId: Long, fromUserId: Long, callType: Int, callerName: String)
        fun onCallResponse(callId: Long, accept: Boolean, toUserId: Long?)
        fun onCallEnded(callId: Long)
    }

    fun setPendingCall(toUserId: Long, isVideo: Boolean) {
        pendingCallToUserId = toUserId
        pendingCallIsVideo = isVideo
        isInCall = true
    }

    fun clearPendingCall() {
        pendingCallToUserId = null
        pendingCallIsVideo = false
        isInCall = false
        shouldIgnoreIncoming = false  // 重置忽略标志
    }

    fun setInCall(inCall: Boolean) {
        isInCall = inCall
    }
    
    // 【新增】设置忽略来电标志，当CallScreen打开时调用
    fun setIgnoreIncoming(ignore: Boolean) {
        shouldIgnoreIncoming = ignore
        if (ignore) {
            isInCall = true
        }
        Log.d(TAG, "setIgnoreIncoming: $ignore")
    }

    fun connect(token: String, userId: Long) {
        if (isConnected) {
            Log.d(TAG, "Already connected, userId=$userId")
            return
        }

        Log.d(TAG, "========== CallManager.connect START ==========")
        Log.d(TAG, "Connecting with userId=$userId")

        val baseUrl = "http://192.168.101.23:8080"
        webSocketService = WebSocketService(baseUrl, token, userId)
        webSocketService?.connect(object : CallWebSocketListener {
            override fun onConnected() {
                Log.d(TAG, "========== CallManager WebSocket Connected ==========")
                Log.d(TAG, "Connected for user $userId")
                isConnected = true
            }

            override fun onDisconnected() {
                Log.d(TAG, "CallManager WebSocket disconnected")
                isConnected = false
            }

            override fun onCallInvite(signal: CallSignal.CallInvite) {
                Log.d(TAG, "========== CallManager onCallInvite ==========")
                Log.d(TAG, "callId=${signal.callId}, from=${signal.fromUserId}, type=${signal.callType}")
                Log.d(TAG, "isInCall=$isInCall, shouldIgnoreIncoming=$shouldIgnoreIncoming")
                
                // 【关键修复】如果当前在通话中或应该忽略来电，忽略call_invite消息
                if (isInCall || shouldIgnoreIncoming) {
                    Log.d(TAG, "Ignoring call_invite - isInCall=$isInCall, shouldIgnoreIncoming=$shouldIgnoreIncoming")
                    return
                }
                
                callListener?.onIncomingCall(
                    signal.callId,
                    signal.fromUserId,
                    signal.callType,
                    "好友"
                )
            }

            override fun onCallInviteConfirm(signal: CallSignal.CallInviteConfirm) {
                Log.d(TAG, "========== CallManager onCallInviteConfirm ==========")
                Log.d(TAG, "callId=${signal.callId}, toUserId=${signal.toUserId}")
                // 如果当前不在通话中，忽略这个回调
                if (!isInCall) {
                    Log.d(TAG, "Not in call, ignoring call_invite_confirm")
                    return
                }
                // 这里不需要额外处理，call_response会处理
            }

            override fun onCallResponse(signal: CallSignal.CallResponse) {
                Log.d(TAG, "========== CallManager onCallResponse ==========")
                Log.d(TAG, "callId=${signal.callId}, accept=${signal.accept}, toUserId=${signal.toUserId}")
                
                // 【关键修复】如果当前不在通话中，忽略call_response回调
                // 这防止了用户挂断后，滞后的响应消息导致再次发起通话
                if (!isInCall) {
                    Log.d(TAG, "Not in call (user ended call), ignoring call_response")
                    clearPendingCall()
                    return
                }
                
                // 使用pendingCallToUserId作为对方ID
                callListener?.onCallResponse(signal.callId, signal.accept, pendingCallToUserId)
            }

            override fun onOffer(signal: CallSignal.SdpOffer) {}
            override fun onAnswer(signal: CallSignal.SdpAnswer) {}
            override fun onIceCandidate(signal: CallSignal.IceCandidate) {}
            override fun onCallEnd(signal: CallSignal.CallEnd) {
                Log.d(TAG, "========== CallManager onCallEnd ==========")
                Log.d(TAG, "callId=${signal.callId}")
                // 清理通话状态
                clearPendingCall()
                // 通知UI结束通话
                callListener?.onCallEnded(signal.callId)
            }
            override fun onError(message: String) {
                Log.e(TAG, "CallManager WebSocket error: $message")
                // 发生错误时也清理状态
                clearPendingCall()
            }
        })

        Log.d(TAG, "CallManager.connect END")
    }

    fun sendCallInvite(fromUserId: Long, toUserId: Long, type: Int) {
        Log.d(TAG, "Sending call invite: from=$fromUserId, to=$toUserId, type=$type")
        setPendingCall(toUserId, type == 2)
        webSocketService?.sendCallInvite(fromUserId, toUserId, type)
    }

    fun sendCallResponse(callId: Long, accept: Boolean) {
        Log.d(TAG, "Sending call response: callId=$callId, accept=$accept")
        webSocketService?.sendCallResponse(callId, accept)
    }

    fun sendOffer(toUserId: Long, sdp: String, fromUserId: Long) {
        webSocketService?.sendOffer(toUserId, sdp, fromUserId)
    }

    fun sendAnswer(toUserId: Long, sdp: String, fromUserId: Long) {
        webSocketService?.sendAnswer(toUserId, sdp, fromUserId)
    }

    fun sendIceCandidate(toUserId: Long, candidate: String, sdpMid: String?, sdpMidIndex: Int?, fromUserId: Long) {
        webSocketService?.sendIceCandidate(toUserId, candidate, sdpMid, sdpMidIndex, fromUserId)
    }

    fun sendCallEnd(callId: Long, toUserId: Long) {
        Log.d(TAG, "Sending call end: callId=$callId, toUserId=$toUserId")
        webSocketService?.sendCallEnd(callId, toUserId)
        // 发送结束后立即清理状态，防止滞后的响应触发再次通话
        clearPendingCall()
    }

    fun disconnect() {
        clearPendingCall()
        webSocketService?.disconnect()
        webSocketService = null
        isConnected = false
    }

    fun isConnected(): Boolean = isConnected
}