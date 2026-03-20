package com.videochat.ui.viewmodel

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videochat.data.api.RetrofitClient
import com.videochat.data.local.PreferencesManager
import com.videochat.data.manager.CallManager
import com.videochat.data.manager.CallNotificationManager
import com.videochat.data.model.CallSignal
import com.videochat.data.repository.CallRepository
import com.videochat.data.websocket.CallWebSocketListener
import com.videochat.data.websocket.WebSocketService
import com.videochat.webrtc.WebRTCManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

sealed class CallState {
    object Idle : CallState()
    object Calling : CallState()
    object Ringing : CallState()
    object Connecting : CallState()
    object Connected : CallState()
    object Ended : CallState()
    data class CallError(val message: String) : CallState()
}

class CallViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "CallViewModel"
        private var instance: CallViewModel? = null

        fun getInstance(application: Application): CallViewModel {
            if (instance == null) {
                instance = CallViewModel(application)
            }
            return instance!!
        }
        
        // 监听是否有进行中的通话（可用于HomeScreen显示"继续通话"按钮）
        fun hasActiveCall(): Boolean {
            val state = instance?._callState?.value
            return state is CallState.Calling || state is CallState.Ringing || 
                   state is CallState.Connecting || state is CallState.Connected
        }
        
        // 获取当前通话的remoteUserId
        fun getCurrentRemoteUserId(): Long = instance?.remoteUserId ?: 0
        fun isVideoCall(): Boolean = instance?.isVideoCall ?: false
    }

    private val preferencesManager = PreferencesManager(application)
    private val apiService = RetrofitClient.apiService
    private val callRepository = CallRepository(apiService)
    private val baseUrl = "http://192.168.101.23:8080"

    private var webSocketService: WebSocketService? = null
    private var webRTCManager: WebRTCManager? = null

    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerOn = MutableStateFlow(true)
    val isSpeakerOn: StateFlow<Boolean> = _isSpeakerOn.asStateFlow()

    private val _isCameraOn = MutableStateFlow(true)
    val isCameraOn: StateFlow<Boolean> = _isCameraOn.asStateFlow()
    
    // 是否有进行中的通话（可用于HomeScreen显示"继续通话"按钮）
    private val _hasActiveCall = MutableStateFlow(false)
    val hasActiveCall: StateFlow<Boolean> = _hasActiveCall.asStateFlow()

private var currentCallId: Long = 0
    var remoteUserId: Long = 0  // 改为public
    var isVideoCall: Boolean = false  // 改为public
    private var callerFlag: Boolean = false
    private var localUserId: Long = 0

    // 标记是否已经初始化过（防止重复初始化）
    private var isInitialized: Boolean = false
    
    // 标记是否明确结束了通话（按结束按钮返回true，按系统返回键/滑动返回为false）
    private var isCallEndedByUser: Boolean = false
    
    // 标记资源是否已经释放（防止重复释放）
    private var isReleased: Boolean = false

    // 重置状态，允许发起新通话
    fun resetState() {
        android.util.Log.d("CallViewModel", "resetState: resetting call state to Idle")
        _callState.value = CallState.Idle
        isInitialized = false
        isCallEndedByUser = false
        isReleased = false
    }

    fun initializeCall(
        callId: Long,
        remoteUserId: Long,
        isVideo: Boolean,
        isCaller: Boolean
) {
    // 【关键修复】如果通话已经结束（Ended）或者正在初始化中，不再重复初始化
    // 这防止了用户挂断后返回HomeScreen时，CallScreen再次发起通话
    val currentState = _callState.value
    if (currentState is CallState.Ended || currentState is CallState.CallError) {
        android.util.Log.w("CallViewModel", "Call already ended or in error state, ignoring initializeCall")
        return
    }
        
        // 防止重复初始化
        if (isInitialized && this.remoteUserId == remoteUserId && this.callerFlag == isCaller) {
            android.util.Log.w("CallViewModel", "Already initialized with same params, ignoring initializeCall")
            return
        }
        
        // 【关键修复】释放旧的WebSocket连接，防止多个连接同时存在
        if (webSocketService != null) {
            android.util.Log.d("CallViewModel", "Releasing old WebSocket before creating new one")
            try {
                webSocketService?.disconnect()
            } catch (e: Exception) {
                android.util.Log.e("CallViewModel", "Error releasing old WebSocket", e)
            }
            webSocketService = null
        }
        
        isInitialized = true
        this.currentCallId = callId
        this.remoteUserId = remoteUserId
        this.isVideoCall = isVideo
        this.callerFlag = isCaller
        
        // 【关键修复】初始化时默认使用听筒，不使用外放
        _isSpeakerOn.value = false

        android.util.Log.d("CallViewModel", "========== initializeCall ==========")
        android.util.Log.d("CallViewModel", "callId=$callId, remoteUserId=$remoteUserId, isVideo=$isVideo, isCaller=$isCaller")

        viewModelScope.launch {
            val token = preferencesManager.getTokenSync() ?: return@launch
            localUserId = preferencesManager.getUserIdSync() ?: return@launch

            // 初始化WebRTC管理器
            webRTCManager = WebRTCManager(getApplication())
            webRTCManager?.initialize(
                onIceCandidate = { candidate, sdpMid ->
                    webSocketService?.sendIceCandidate(
                        remoteUserId,
                        candidate.sdp,
                        sdpMid,
                        candidate.sdpMLineIndex,
                        localUserId
                    )
                },
                onRemoteStream = { stream ->
                    android.util.Log.d("CallViewModel", "========== onRemoteStream RECEIVED ==========")
                    android.util.Log.d("CallViewModel", "remote stream: videoTracks=${stream.videoTracks.size}, audioTracks=${stream.audioTracks.size}")
                },
                onIceConnectionState = { state ->
                    android.util.Log.d("CallViewModel", "========== onIceConnectionState: $state ==========")
                    when (state) {
            PeerConnection.IceConnectionState.CONNECTED -> {
                _callState.value = CallState.Connected
                android.util.Log.d("CallViewModel", "Call connected!")
                // 【关键修复】通话连接成功后设置音频输出为听筒
                webRTCManager?.setSpeakerEnabled(_isSpeakerOn.value)
                // 显示通知栏通知
                CallNotificationManager.showCallNotification(
                    getApplication(),
                    "好友",
                    isVideoCall
                )
            }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _callState.value = CallState.Ended
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _callState.value = CallState.CallError("连接失败")
                        }
                        else -> {}
                    }
                }
            )

            // 初始化WebSocket
            webSocketService = WebSocketService(baseUrl, token, localUserId)
            webSocketService?.connect(object : CallWebSocketListener {
                override fun onConnected() {
                    android.util.Log.d("CallViewModel", "========== WebSocket Connected ==========")
                    android.util.Log.d("CallViewModel", "localUserId=$localUserId, remoteUserId=$remoteUserId, isCaller=$callerFlag")

                    // 【修改点】移除了对 startCaptureAndAddTracks 的调用

                    if (callerFlag) {
                        // 主叫：连接成功后发送呼叫邀请
                        _callState.value = CallState.Calling
                        android.util.Log.d("CallViewModel", "Sending call invite...")
                        webSocketService?.sendCallInvite(localUserId, remoteUserId, if (isVideoCall) 2 else 1)
                        android.util.Log.d("CallViewModel", "Sent call invite")
                    } else {
                        // 被叫：等待来电
                        _callState.value = CallState.Ringing
                        android.util.Log.d("CallViewModel", "Waiting for incoming call...")
                    }
                }

                override fun onDisconnected() {
                    android.util.Log.d("CallViewModel", "WebSocket disconnected")
                    // 【关键修复】只有在活跃通话中断开才设置为Ended
                    // 防止旧连接断开影响新通话
                    val currentState = _callState.value
                    if (currentState is CallState.Connecting || currentState is CallState.Connected) {
                        android.util.Log.d("CallViewModel", "WebSocket disconnected during active call, setting state to Ended")
                        _callState.value = CallState.Ended
                    } else {
                        android.util.Log.d("CallViewModel", "WebSocket disconnected but state is $currentState, not changing state")
                    }
                }

                override fun onCallInvite(signal: CallSignal.CallInvite) {
                    android.util.Log.d("CallViewModel", "Received call invite, callId=${signal.callId}")
                    currentCallId = signal.callId
                    isVideoCall = signal.callType == 2
                    callerFlag = false
                    _callState.value = CallState.Ringing
                }

                override fun onCallInviteConfirm(signal: CallSignal.CallInviteConfirm) {
                    // 收到服务器确认，保存callId
                    currentCallId = signal.callId
                    android.util.Log.d("CallViewModel", "Received call invite confirm, callId=$callId")
                }

                override fun onCallResponse(signal: CallSignal.CallResponse) {
                    android.util.Log.d("CallViewModel", "========== onCallResponse RECEIVED ==========")
                    android.util.Log.d("CallViewModel", "callId=${signal.callId}, accept=${signal.accept}, toUserId=${signal.toUserId}")
                    android.util.Log.d("CallViewModel", "currentCallId=${currentCallId}, remoteUserId=${remoteUserId}, isCaller=${callerFlag}")

                    if (signal.accept) {
                        android.util.Log.d("CallViewModel", "Call accepted, starting WebRTC...")
                        _callState.value = CallState.Connecting
                        startWebRTCCall()
                    } else {
                        android.util.Log.d("CallViewModel", "Call rejected")
                        _callState.value = CallState.Ended
                    }
                }

                override fun onOffer(signal: CallSignal.SdpOffer) {
                    android.util.Log.d("CallViewModel", "========== onOffer RECEIVED ==========")
                    android.util.Log.d("CallViewModel", "sdp length: ${signal.sdp?.length ?: 0}")
                    android.util.Log.d("CallViewModel", "isVideoCall BEFORE setRemoteDescription: $isVideoCall")
                    _callState.value = CallState.Connecting
                    // 只设置远程描述，不立即创建Answer
                    // Answer会在用户点击接听按钮后创建
                    webRTCManager?.setRemoteDescription(signal.sdp, "offer") { success ->
                        android.util.Log.d("CallViewModel", "setRemoteDescription(offer) result: $success")
                        // 不在这里创建Answer，等待用户点击接听
                    }
                }

                override fun onAnswer(signal: CallSignal.SdpAnswer) {
                    android.util.Log.d("CallViewModel", "========== onAnswer RECEIVED ==========")
                    android.util.Log.d("CallViewModel", "sdp length: ${signal.sdp?.length ?: 0}")
                    webRTCManager?.setRemoteDescription(signal.sdp, "answer") { success ->
                        android.util.Log.d("CallViewModel", "setRemoteDescription(answer) result: $success")
                    }
                }

                override fun onIceCandidate(signal: CallSignal.IceCandidate) {
                    android.util.Log.d("CallViewModel", "========== onIceCandidate RECEIVED ==========")
                    android.util.Log.d("CallViewModel", "candidate: ${signal.candidate}")
                    webRTCManager?.addIceCandidate(
                        signal.candidate,
                        signal.sdpMid,
                        signal.sdpMidIndex ?: 0
                    )
                }

                override fun onCallEnd(signal: CallSignal.CallEnd) {
                    android.util.Log.d("CallViewModel", "Received call end")
                    _callState.value = CallState.Ended
                    release()
                }

                override fun onError(message: String) {
                    android.util.Log.e("CallViewModel", "WebSocket error: $message")
                    _callState.value = CallState.CallError(message)
                }
            })
        }
    }

    private fun startWebRTCCall() {
        android.util.Log.d("CallViewModel", "========== startWebRTCCall ==========")
        android.util.Log.d("CallViewModel", "isVideoCall=$isVideoCall, remoteUserId=$remoteUserId")

        // 【修改点】移除了对 startCaptureAndAddTracks 的调用

        webRTCManager?.createOffer(isVideoCall) { offerSdp ->
            android.util.Log.d("CallViewModel", "Offer created: ${offerSdp != null}")
            offerSdp?.let {
                android.util.Log.d("CallViewModel", "Sending offer, sdp length: ${it.description.length}")
                webSocketService?.sendOffer(remoteUserId, it.description, localUserId)
            }
        }
    }

    fun startLocalVideo(surfaceView: org.webrtc.SurfaceViewRenderer) {
        if (isVideoCall) {
            webRTCManager?.startLocalVideo(surfaceView)
        }
    }

    fun setRemoteVideoView(surfaceView: org.webrtc.SurfaceViewRenderer) {
        webRTCManager?.setRemoteView(surfaceView)
    }

    fun resetViews() {
        webRTCManager?.resetViews()
    }

    fun acceptCall() {
        android.util.Log.d("CallViewModel", "========== acceptCall START ==========")
        webSocketService?.sendCallResponse(currentCallId, true)
        _callState.value = CallState.Connecting

        // 【修改点】移除了对 startCaptureAndAddTracks 的调用

        // 创建Answer - WebRTCManager会自动等待远程描述设置完成
        android.util.Log.d("CallViewModel", "Creating answer...")
        webRTCManager?.createAnswer(isVideoCall) { answerSdp ->
            android.util.Log.d("CallViewModel", "Answer created: ${answerSdp != null}")
            if (answerSdp != null) {
                android.util.Log.d("CallViewModel", "Sending answer...")
                webSocketService?.sendAnswer(remoteUserId, answerSdp.description, localUserId)
            } else {
                android.util.Log.e("CallViewModel", "Failed to create answer!")
            }
        }
    }

    fun rejectCall() {
        webSocketService?.sendCallResponse(currentCallId, false)
        _callState.value = CallState.Ended
        
        // 【关键修复】通知CallManager清理状态
        try {
            val callManager = CallManager.getInstance(getApplication())
            callManager.clearPendingCall()
            callManager.setInCall(false)
            callManager.setIgnoreIncoming(false)  // 重置忽略标志
        } catch (e: Exception) {
            android.util.Log.e("CallViewModel", "Error clearing CallManager state", e)
        }
        
        release()
    }

    fun endCall() {
        android.util.Log.d("CallViewModel", "endCall called, currentCallId=$currentCallId, remoteUserId=$remoteUserId, webSocketService=$webSocketService")
        // Use the callId received from server (via call_invite_confirm)
        val callIdToUse = if (currentCallId > 0) currentCallId else remoteUserId
        try {
            webSocketService?.sendCallEnd(callIdToUse, remoteUserId)
            android.util.Log.d("CallViewModel", "Sent call end message")
        } catch (e: Exception) {
            android.util.Log.e("CallViewModel", "Error sending call end", e)
        }
        android.util.Log.d("CallViewModel", "Setting state to Ended")
        _callState.value = CallState.Ended

        // 【关键修复】通知CallManager清理状态
        try {
            val callManager = CallManager.getInstance(getApplication())
            callManager.clearPendingCall()
            callManager.setInCall(false)
            callManager.setIgnoreIncoming(false)  // 重置忽略标志
        } catch (e: Exception) {
            android.util.Log.e("CallViewModel", "Error clearing CallManager state", e)
        }

        // 延迟释放资源，确保消息发送完成
        viewModelScope.launch {
            kotlinx.coroutines.delay(500) // 等待500ms让消息发送出去
            android.util.Log.d("CallViewModel", "Calling release after delay")
            release()
            android.util.Log.d("CallViewModel", "release done")
        }
    }

fun forceEndCall() {
        android.util.Log.d("CallViewModel", "forceEndCall called, currentCallId=$currentCallId, remoteUserId=$remoteUserId")
        
        // 标记用户明确结束了通话
        isCallEndedByUser = true
        // 重置初始化标志，允许下次通话
        isInitialized = false

        // 发送结束通话消息给对方
        if (currentCallId > 0 && remoteUserId > 0) {
            webSocketService?.sendCallEnd(currentCallId, remoteUserId)
            android.util.Log.d("CallViewModel", "Sent call_end to remoteUserId=$remoteUserId")
        }

        _callState.value = CallState.Ended
        
        // 【关键修复】通知CallManager清理状态，防止滞后的消息触发新通话
        try {
            val callManager = CallManager.getInstance(getApplication())
            callManager.clearPendingCall()
            callManager.setInCall(false)
            callManager.setIgnoreIncoming(false)  // 重置忽略标志
            android.util.Log.d("CallViewModel", "CallManager state cleared")
        } catch (e: Exception) {
            android.util.Log.e("CallViewModel", "Error clearing CallManager state", e)
        }
        
        release()
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        webRTCManager?.setAudioEnabled(!_isMuted.value)
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
        // 【关键修复】设置音频路由：true=外放(speaker), false=听筒(earpiece)
        webRTCManager?.setSpeakerEnabled(_isSpeakerOn.value)
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
        webRTCManager?.setVideoEnabled(_isCameraOn.value)
    }

    fun switchCamera() {
        // 【修复】恢复摄像头切换功能
        webRTCManager?.switchCamera()
    }

    private fun release() {
        // 防止重复释放
        if (isReleased) {
            android.util.Log.d("CallViewModel", "release: already released, skipping")
            return
        }
        isReleased = true
        
        android.util.Log.d("CallViewModel", "release: releasing resources")
        webRTCManager?.release()
        webRTCManager = null
        webSocketService?.disconnect()
        webSocketService = null
        // 取消通知栏通知
        CallNotificationManager.cancelCallNotification(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        // 只有用户明确点击"结束通话"按钮时才释放资源
        // 如果用户只是按返回键/滑动返回，不释放资源，让通话继续在后台运行
        if (isCallEndedByUser) {
            android.util.Log.d("CallViewModel", "onCleared: user ended call, releasing resources")
            release()
        } else {
            android.util.Log.d("CallViewModel", "onCleared: user just navigated back, keeping resources")
            // 不释放资源，但重置状态
            _callState.value = CallState.Idle
            // 【关键修复】重置isInitialized，允许下次发起新通话
            isInitialized = false
            // 重置isReleased，允许下次通话时释放资源
            isReleased = false
        }
    }
}