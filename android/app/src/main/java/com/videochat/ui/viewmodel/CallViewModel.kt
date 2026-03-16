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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val baseUrl = "http://10.0.2.2:8080"

    private var webSocketService: WebSocketService? = null

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

        viewModelScope.launch {
            val token = preferencesManager.getTokenSync() ?: return@launch

            webSocketService = WebSocketService(baseUrl, token)
            webSocketService?.connect(object : CallWebSocketListener {
                override fun onConnected() {
                    if (!callerFlag) {
                        _callState.value = CallState.Ringing
                    }
                }

                override fun onDisconnected() {
                    _callState.value = CallState.Ended
                }

                override fun onCallInvite(signal: CallSignal.CallInvite) {
                    currentCallId = signal.callId
                    isVideoCall = signal.callType == 2
                    callerFlag = false
                    _callState.value = CallState.Ringing
                }

                override fun onCallResponse(signal: CallSignal.CallResponse) {
                    if (signal.accept) {
                        _callState.value = CallState.Connected
                    } else {
                        _callState.value = CallState.Ended
                    }
                }

                override fun onOffer(signal: CallSignal.SdpOffer) {
                    _callState.value = CallState.Connected
                }

                override fun onAnswer(signal: CallSignal.SdpAnswer) {}
                override fun onIceCandidate(signal: CallSignal.IceCandidate) {}
                override fun onCallEnd(signal: CallSignal.CallEnd) {
                    _callState.value = CallState.Ended
                    release()
                }

                override fun onError(message: String) {
                    _callState.value = CallState.Error(message)
                }
            })

            if (callerFlag) {
                _callState.value = CallState.Calling
            }
        }
    }

    fun acceptCall() {
        webSocketService?.sendCallResponse(currentCallId, true)
        _callState.value = CallState.Connected
    }

    fun rejectCall() {
        webSocketService?.sendCallResponse(currentCallId, false)
        _callState.value = CallState.Ended
        release()
    }

    fun endCall() {
        webSocketService?.sendCallEnd(currentCallId, remoteUserId)
        _callState.value = CallState.Ended
        release()
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleSpeaker() {
        _isSpeakerOn.value = !_isSpeakerOn.value
    }

    fun toggleCamera() {
        _isCameraOn.value = !_isCameraOn.value
    }

    fun switchCamera() {}

    private fun release() {
        webSocketService?.disconnect()
        webSocketService = null
    }

    override fun onCleared() {
        super.onCleared()
        release()
    }
}