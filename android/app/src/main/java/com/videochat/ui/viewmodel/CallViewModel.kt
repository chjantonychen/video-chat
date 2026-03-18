package com.videochat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videochat.data.api.RetrofitClient
import com.videochat.data.local.PreferencesManager
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
    data class Error(val message: String) : CallState()
}

class CallViewModel(application: Application) : AndroidViewModel(application) {

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

    private var currentCallId: Long = 0
    private var remoteUserId: Long = 0
    private var isVideoCall: Boolean = false
    private var callerFlag: Boolean = false
    private var localUserId: Long = 0

    fun initializeCall(
        callId: Long,
        remoteUserId: Long,
        isVideo: Boolean,
        isCaller: Boolean
    ) {
        this.currentCallId = callId
        this.remoteUserId = remoteUserId
        this.isVideoCall = isVideo
        this.callerFlag = isCaller
        
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
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _callState.value = CallState.Ended
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _callState.value = CallState.Error("连接失败")
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
                    _callState.value = CallState.Ended
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
                    _callState.value = CallState.Error(message)
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
        
        // 发送结束通话消息给对方
        if (currentCallId != null && currentCallId > 0 && remoteUserId != null && remoteUserId > 0) {
            webSocketService?.sendCallEnd(currentCallId, remoteUserId)
            android.util.Log.d("CallViewModel", "Sent call_end to remoteUserId=$remoteUserId")
        }
        
        _callState.value = CallState.Ended
        release()
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        webRTCManager?.setAudioEnabled(!_isMuted.value)
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
        webRTCManager?.setVideoEnabled(_isCameraOn.value)
    }

    fun switchCamera() {
        webRTCManager?.switchCamera()
    }

    private fun release() {
        webRTCManager?.release()
        webRTCManager = null
        webSocketService?.disconnect()
        webSocketService = null
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }
}