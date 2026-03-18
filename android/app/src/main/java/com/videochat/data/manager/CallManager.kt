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
    }
    
    fun clearPendingCall() {
        pendingCallToUserId = null
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
                callListener?.onIncomingCall(
                    signal.callId, 
                    signal.fromUserId, 
                    signal.callType, 
                    "好友"
                )
            }
            
            override fun onCallInviteConfirm(signal: CallSignal.CallInviteConfirm) {}
            
            override fun onCallResponse(signal: CallSignal.CallResponse) {
                Log.d(TAG, "========== CallManager onCallResponse ==========")
                Log.d(TAG, "callId=${signal.callId}, accept=${signal.accept}, toUserId=${signal.toUserId}")
                // 使用pendingCallToUserId作为对方ID
                callListener?.onCallResponse(signal.callId, signal.accept, pendingCallToUserId)
            }
            
            override fun onOffer(signal: CallSignal.SdpOffer) {}
            override fun onAnswer(signal: CallSignal.SdpAnswer) {}
            override fun onIceCandidate(signal: CallSignal.IceCandidate) {}
            override fun onCallEnd(signal: CallSignal.CallEnd) {
                Log.d(TAG, "========== CallManager onCallEnd ==========")
                Log.d(TAG, "callId=${signal.callId}")
                // 通知UI结束通话
                callListener?.onCallEnded(signal.callId)
            }
            override fun onError(message: String) {
                Log.e(TAG, "CallManager WebSocket error: $message")
            }
        })
        
        Log.d(TAG, "CallManager.connect END")
    }
    
    fun sendCallInvite(fromUserId: Long, toUserId: Long, type: Int) {
        Log.d(TAG, "Sending call invite: from=$fromUserId, to=$toUserId, type=$type")
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
        webSocketService?.sendCallEnd(callId, toUserId)
    }
    
    fun disconnect() {
        webSocketService?.disconnect()
        webSocketService = null
        isConnected = false
    }
    
    fun isConnected(): Boolean = isConnected
}
