# Android端WebRTC语音视频通话实现计划

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan.

**Goal:** 在Android端实现完整的WebRTC语音/视频通话功能，包括STOMP WebSocket客户端、WebRTC管理器、通话ViewModel和完善的通话界面。

**Architecture:** 采用分层架构 - UI层(CallScreen) → ViewModel层(CallViewModel) → WebRTC层(WebRTCManager) → 网络层(WebSocketService)。WebRTC管理器和WebSocket服务解耦，通过ViewModel进行状态管理。

**Tech Stack:** Kotlin, Jetpack Compose, libwebrtc, OkHttp WebSocket, Gson

---

## 项目文件结构

```
android/app/src/main/java/com/videochat/
├── data/
│   ├── api/
│   │   └── ApiService.kt                    # 现有，已包含通话API
│   ├── model/
│   │   ├── CallRecord.kt                    # 需要创建: 通话记录模型
│   │   └── CallSignal.kt                    # 需要创建: WebSocket信令模型
│   ├── repository/
│   │   └── CallRepository.kt                # 需要创建: 通话仓库
│   └── websocket/
│       ├── WebSocketService.kt              # 需要创建: STOMP WebSocket服务
│       └── WebSocketListener.kt             # 需要创建: WebSocket监听器
├── ui/
│   ├── screens/
│   │   └── CallScreen.kt                    # 现有: 需要大幅完善
│   └── viewmodel/
│       └── CallViewModel.kt                 # 需要创建: 通话ViewModel
└── webrtc/
    ├── WebRTCManager.kt                     # 需要创建: WebRTC核心管理器
    └── PeerConnectionObserver.kt            # 需要创建: 连接观察者
```

---

## Chunk 1: 添加依赖和配置

### Task 1.1: 更新build.gradle.kts添加WebRTC和WebSocket依赖

**Files:**
- Modify: `android/app/build.gradle.kts:75`

- [ ] **Step 1: 添加WebRTC和STOMP依赖**

```kotlin
// 在dependencies块中添加:

// WebRTC
implementation("org.webrtc:google-webrtc:1.0.32006")

// STOMP WebSocket客户端
implementation("com.github.akhgul:stomp-websocket-android:1.0.0")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
```

- [ ] **Step 2: 添加网络权限**

检查 `android/app/src/main/AndroidManifest.xml` 是否包含:
```xml
<uses-permission android:name="android.permission.INTERNET"/>
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
<uses-permission android:name="android.permission.CAMERA"/>
<uses-permission android:name="android.permission.RECORD_AUDIO"/>
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
```

---

## Chunk 2: 数据模型

### Task 2.1: 创建CallRecord模型

**Files:**
- Create: `android/app/src/main/java/com/videochat/data/model/CallRecord.kt`

- [ ] **Step 1: 编写CallRecord数据类**

```kotlin
package com.videochat.data.model

import com.google.gson.annotations.SerializedName

data class CallRecord(
    val id: Long = 0,
    @SerializedName("caller_id")
    val callerId: Long = 0,
    @SerializedName("callee_id")
    val calleeId: Long = 0,
    val type: Int = 1,           // 1=语音, 2=视频
    val status: Int = 0,         // 0=未接, 1=已接, 2=拒接
    val duration: Int = 0,       // 通话时长(秒)
    @SerializedName("created_at")
    val createdAt: String = ""
)
```

### Task 2.2: 创建CallSignal信令模型

**Files:**
- Create: `android/app/src/main/java/com/videochat/data/model/CallSignal.kt`

- [ ] **Step 1: 编写信令消息模型**

```kotlin
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
```

---

## Chunk 3: WebSocket服务

### Task 3.1: 创建WebSocket监听器

**Files:**
- Create: `android/app/src/main/java/com/videochat/data/websocket/WebSocketListener.kt`

- [ ] **Step 1: 编写WebSocketListener接口**

```kotlin
package com.videochat.data.websocket

import com.videochat.data.model.CallSignal

interface WebSocketListener {
    fun onConnected()
    fun onDisconnected()
    fun onCallInvite(signal: CallSignal.CallInvite)
    fun onCallResponse(signal: CallSignal.CallResponse)
    fun onOffer(signal: CallSignal.SdpOffer)
    fun onAnswer(signal: CallSignal.SdpAnswer)
    fun onIceCandidate(signal: CallSignal.IceCandidate)
    fun onCallEnd(signal: CallSignal.CallEnd)
    fun onError(message: String)
}
```

### Task 3.2: 创建WebSocketService

**Files:**
- Create: `android/app/src/main/java/com/videochat/data/websocket/WebSocketService.kt`

- [ ] **Step 1: 编写WebSocketService**

```kotlin
package com.videochat.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.videochat.data.model.CallSignal
import com.videochat.data.model.SignalMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketService(
    private val baseUrl: String,
    private val token: String
) {
    companion object {
        private const val TAG = "WebSocketService"
    }

    private var webSocket: WebSocket? = null
    private var listener: WebSocketListener? = null
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(listener: WebSocketListener) {
        this.listener = listener
        val request = Request.Builder()
            .url("$baseUrl/ws?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                listener.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
                parseMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                listener.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: ${t.message}")
                listener.onError(t.message ?: "Unknown error")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        scope.cancel()
    }

    fun sendCallInvite(fromUserId: Long, toUserId: Long, type: Int) {
        val message = mapOf(
            "fromUserId" to fromUserId.toString(),
            "toUserId" to toUserId.toString(),
            "type" to type.toString()
        )
        sendMessage("/app/call/invite", message)
    }

    fun sendCallResponse(callId: Long, accept: Boolean) {
        val message = mapOf(
            "callId" to callId.toString(),
            "accept" to accept.toString()
        )
        sendMessage("/app/call/response", message)
    }

    fun sendOffer(toUserId: Long, sdp: String, fromUserId: Long) {
        val message = mapOf(
            "toUserId" to toUserId.toString(),
            "sdp" to sdp,
            "fromUserId" to fromUserId.toString()
        )
        sendMessage("/app/call/offer", message)
    }

    fun sendAnswer(toUserId: Long, sdp: String, fromUserId: Long) {
        val message = mapOf(
            "toUserId" to toUserId.toString(),
            "sdp" to sdp,
            "fromUserId" to fromUserId.toString()
        )
        sendMessage("/app/call/answer", message)
    }

    fun sendIceCandidate(toUserId: Long, candidate: String, sdpMid: String?, sdpMidIndex: Int?, fromUserId: Long) {
        val message = mutableMapOf(
            "toUserId" to toUserId.toString(),
            "candidate" to candidate,
            "fromUserId" to fromUserId.toString()
        )
        sdpMid?.let { message["sdpMid"] = it }
        sdpMidIndex?.let { message["sdpMidIndex"] = it.toString() }
        sendMessage("/app/call/ice", message)
    }

    fun sendCallEnd(callId: Long, toUserId: Long) {
        val message = mapOf(
            "callId" to callId.toString(),
            "toUserId" to toUserId.toString()
        )
        sendMessage("/app/call/end", message)
    }

    private fun sendMessage(destination: String, data: Map<String, String>) {
        val fullMessage = mapOf(
            "destination" to destination,
            "body" to gson.toJson(data)
        )
        webSocket?.send(gson.toJson(fullMessage))
    }

    private fun parseMessage(text: String) {
        try {
            // 处理STOMP消息格式
            val message = gson.fromJson(text, Map::class.java)
            val body = message["body"]?.toString() ?: return
            val signal = gson.fromJson(body, SignalMessage::class.java)

            when (signal.type) {
                "call_invite" -> {
                    listener?.onCallInvite(CallSignal.CallInvite(
                        callId = signal.callId!!,
                        fromUserId = signal.fromUserId!!,
                        callType = signal.callType!!
                    ))
                }
                "call_response" -> {
                    listener?.onCallResponse(CallSignal.CallResponse(
                        callId = signal.callId!!,
                        accept = signal.accept!!
                    ))
                }
                "offer" -> {
                    listener?.onOffer(CallSignal.SdpOffer(
                        sdp = signal.sdp!!,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "answer" -> {
                    listener?.onAnswer(CallSignal.SdpAnswer(
                        sdp = signal.sdp!!,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "ice_candidate" -> {
                    listener?.onIceCandidate(CallSignal.IceCandidate(
                        candidate = signal.candidate!!,
                        sdpMid = signal.sdpMid,
                        sdpMidIndex = signal.sdpMidIndex,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "call_end" -> {
                    listener?.onCallEnd(CallSignal.CallEnd(
                        callId = signal.callId!!
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
}
```

---

## Chunk 4: WebRTC管理器

### Task 4.1: 创建PeerConnectionObserver

**Files:**
- Create: `android/app/src/main/java/com/videochat/webrtc/PeerConnectionObserver.kt`

- [ ] **Step 1: 编写PeerConnectionObserver**

```kotlin
package com.videochat.webrtc

import android.util.Log
import org.webrtc.*

class PeerConnectionObserver(
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onIceConnectionStateChanged: (PeerConnection.IceConnectionState) -> Unit,
    private val onSignalingStateChanged: (PeerConnection.SignalingState) -> Unit,
    private val onDataChannel: (DataChannel) -> Unit
) : PeerConnection.Observer {

    companion object {
        private const val TAG = "PeerConnectionObserver"
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            Log.d(TAG, "onIceCandidate: $it")
            onIceCandidate(it)
        }
    }

    override fun onIceConnectionStateChange(state: PeerConnection.IceConnectionState) {
        Log.d(TAG, "onIceConnectionStateChange: $state")
        onIceConnectionStateChanged(state)
    }

    override fun onSignalingState(state: PeerConnection.SignalingState) {
        Log.d(TAG, "onSignalingState: $state")
        onSignalingStateChanged(state)
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Log.d(TAG, "onIceCandidatesRemoved")
    }

    override fun onDataChannel(channel: DataChannel?) {
        channel?.let {
            Log.d(TAG, "onDataChannel: ${it.label()}")
            onDataChannel(it)
        }
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "onRenegotiationNeeded")
    }

    override fun onAddStream(stream: MediaStream?) {
        Log.d(TAG, "onAddStream: ${stream?.label()}")
    }

    override fun onRemoveStream(stream: MediaStream?) {
        Log.d(TAG, "onRemoveStream")
    }

    override fun onTrack(transceiver: RtpTransceiver?) {
        Log.d(TAG, "onTrack")
    }
}
```

### Task 4.2: 创建WebRTCManager

**Files:**
- Create: `android/app/src/main/java/com/videochat/webrtc/WebRTCManager.kt`

- [ ] **Step 1: 编写WebRTCManager**

```kotlin
package com.videochat.webrtc

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.concurrent.Executors

class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
    }

    private var peerConnection: PeerConnection? = null
    private var localVideoTrack: VideoTrack? = null
    private var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var eglBase: EglBase? = null

    private val executor = Executors.newSingleThreadExecutor()
    private var onLocalIceCandidate: ((IceCandidate, String) -> Unit)? = null
    private var onRemoteStream: ((MediaStream) -> Unit)? = null

    fun initialize(
        onIceCandidate: (IceCandidate, String) -> Unit,
        onRemoteStream: (MediaStream) -> Unit
    ) {
        this.onLocalIceCandidate = onIceCandidate
        this.onRemoteStream = onRemoteStream

        executor.execute {
            // 初始化PeerConnectionFactory
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            eglBase = EglBase.create()
            createPeerConnection()
        }
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            arrayListOf(
                PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = PeerConnectionFactory.createPeerConnection(
            rtcConfig,
            PeerConnectionObserver(
                onIceCandidate = { candidate ->
                    onLocalIceCandidate?.invoke(candidate, candidate.sdpMid ?: "")
                },
                onIceConnectionStateChanged = { state ->
                    Log.d(TAG, "IceConnectionState: $state")
                },
                onSignalingStateChanged = { state ->
                    Log.d(TAG, "SignalingState: $state")
                },
                onDataChannel = { channel ->
                    Log.d(TAG, "DataChannel: ${channel.label()}")
                }
            )
        )
    }

    fun createOffer(isVideoCall: Boolean) {
        executor.execute {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideoCall.toString()))
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, it)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failed: $error")
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set offer failed: $error")
                }
            }, constraints)
        }
    }

    fun createAnswer(isVideoCall: Boolean) {
        executor.execute {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideoCall.toString()))
            }

            peerConnection?.createAnswer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, it)
                    }
                }

                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create answer failed: $error")
                }
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set answer failed: $error")
                }
            }, constraints)
        }
    }

    fun setRemoteDescription(sdp: String, type: String) {
        executor.execute {
            val sessionDescription = SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type),
                sdp
            )
            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description failed: $error")
                }
            }, sessionDescription)
        }
    }

    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        executor.execute {
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
            peerConnection?.addIceCandidate(iceCandidate)
        }
    }

    fun startLocalVideoCapture(surfaceView: SurfaceViewRenderer) {
        executor.execute {
            // 设置视频渲染视图
            surfaceView.init(eglBase?.eglBaseContext, null)
            surfaceView.setMirror(true)

            // 创建媒体约束
            val audioConstraints = MediaConstraints()
            val videoConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("minWidth", "1280"))
                mandatory.add(MediaConstraints.KeyValuePair("minHeight", "720"))
                mandatory.add(MediaConstraints.KeyValuePair("minFrameRate", "30"))
            }

            // 创建音视频源
            val audioSource = PeerConnectionFactory.createAudioSource(audioConstraints)
            val videoSource = PeerConnectionFactory.createVideoSource(videoConstraints)

            // 创建音视频轨道
            localAudioTrack = PeerConnectionFactory.createAudioTrack("audio_track", audioSource)
            localVideoTrack = PeerConnectionFactory.createVideoTrack("video_track", videoSource)

            // 绑定渲染视图
            localVideoTrack?.addSink(surfaceView)

            // 获取摄像头
            videoCapturer = Camera2Capturer(context, null)
            videoCapturer?.startCapture(1280, 720)

            // 添加到PeerConnection
            val audioConstraints2 = MediaConstraints()
            val videoConstraints2 = MediaConstraints()

            val audioTrack = localAudioTrack
            val videoTrack = localVideoTrack

            peerConnection?.addTrack(audioTrack, listOf("stream"))
            if (videoTrack != null) {
                peerConnection?.addTrack(videoTrack, listOf("stream"))
            }
        }
    }

    fun stopLocalVideoCapture() {
        executor.execute {
            videoCapturer?.stopCapture()
            videoCapturer = null
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun switchCamera() {
        (videoCapturer as? Camera2Capturer)?.switchCamera(null)
    }

    fun getLocalDescription(): String? {
        return peerConnection?.localDescription?.description
    }

    fun release() {
        executor.execute {
            stopLocalVideoCapture()
            peerConnection?.close()
            peerConnection = null
            eglBase?.release()
            eglBase = null
        }
    }
}
```

---

## Chunk 5: 通话仓库

### Task 5.1: 创建CallRepository

**Files:**
- Create: `android/app/src/main/java/com/videochat/data/repository/CallRepository.kt`

- [ ] **Step 1: 编写CallRepository**

```kotlin
package com.videochat.data.repository

import com.videochat.data.api.ApiService
import com.videochat.data.model.CallRecord
import com.videochat.data.websocket.WebSocketService

class CallRepository(
    private val apiService: ApiService,
    private val webSocketService: WebSocketService
) {
    suspend fun getCallHistory(page: Int = 1, size: Int = 20): Result<List<CallRecord>> {
        return try {
            val response = apiService.getCallHistory(page, size)
            if (response.isSuccessful) {
                Result.success(response.body() ?: emptyList())
            } else {
                Result.failure(Exception("Failed to get call history"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun connectWebSocket() {
        // WebSocket连接由ViewModel管理
    }
}
```

---

## Chunk 6: 通话ViewModel

### Task 6.1: 创建CallViewModel

**Files:**
- Create: `android/app/src/main/java/com/videochat/ui/viewmodel/CallViewModel.kt`

- [ ] **Step 1: 编写CallViewModel**

```kotlin
package com.videochat.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.videochat.data.api.RetrofitClient
import com.videochat.data.local.PreferencesManager
import com.videochat.data.model.CallSignal
import com.videochat.data.repository.CallRepository
import com.videochat.data.websocket.WebSocketListener
import com.videochat.data.websocket.WebSocketService
import com.videochat.webrtc.WebRTCManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.MediaStream

// 通话状态
sealed class CallState {
    object Idle : CallState()
    object Calling : CallState()
    object Ringing : CallState()
    object Connected : CallState()
    object Ended : CallState()
    data class Error(val message: String) : CallState()
}

class CallViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val apiService = RetrofitClient.createService(preferencesManager)
    private val baseUrl = "http://10.0.2.2:8080" // Android模拟器访问本机

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
    private var isCaller: Boolean = false

    fun initializeCall(
        callId: Long,
        remoteUserId: Long,
        isVideo: Boolean,
        isCaller: Boolean
    ) {
        this.currentCallId = callId
        this.remoteUserId = remoteUserId
        this.isVideoCall = isVideo
        this.isCaller = isCaller

        val token = preferencesManager.getToken() ?: return
        val userId = preferencesManager.getUserId()

        // 初始化WebSocket
        webSocketService = WebSocketService(baseUrl, token)
        webSocketService?.connect(object : WebSocketListener {
            override fun onConnected() {
                if (!isCaller) {
                    _callState.value = CallState.Ringing
                }
            }

            override fun onDisconnected() {
                _callState.value = CallState.Ended
            }

            override fun onCallInvite(signal: CallSignal.CallInvite) {
                currentCallId = signal.callId
                isVideoCall = signal.callType == 2
                isCaller = false
                _callState.value = CallState.Ringing
            }

            override fun onCallResponse(signal: CallSignal.CallResponse) {
                if (signal.accept) {
                    _callState.value = CallState.Connected
                    startWebRTC()
                } else {
                    _callState.value = CallState.Ended
                }
            }

            override fun onOffer(signal: CallSignal.SdpOffer) {
                webRTCManager?.setRemoteDescription(signal.sdp, "offer")
                webRTCManager?.createAnswer(isVideoCall)
            }

            override fun onAnswer(signal: CallSignal.SdpAnswer) {
                webRTCManager?.setRemoteDescription(signal.sdp, "answer")
            }

            override fun onIceCandidate(signal: CallSignal.IceCandidate) {
                webRTCManager?.addIceCandidate(
                    signal.candidate,
                    signal.sdpMid,
                    signal.sdpMidIndex ?: 0
                )
            }

            override fun onCallEnd(signal: CallSignal.CallEnd) {
                _callState.value = CallState.Ended
                release()
            }

            override fun onError(message: String) {
                _callState.value = CallState.Error(message)
            }
        })

        // 初始化WebRTC
        webRTCManager = WebRTCManager(application)
        webRTCManager?.initialize(
            onIceCandidate = { candidate, sdpMid ->
                userId?.let { uid ->
                    webSocketService?.sendIceCandidate(
                        remoteUserId,
                        candidate.sdp,
                        sdpMid,
                        candidate.sdpMLineIndex,
                        uid
                    )
                }
            },
            onRemoteStream = { stream ->
                // 可以通过回调处理远程流
            }
        )

        if (isCaller) {
            _callState.value = CallState.Calling
        }
    }

    fun acceptCall() {
        webSocketService?.sendCallResponse(currentCallId, true)
        _callState.value = CallState.Connected
        startWebRTC()
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

    private fun startWebRTC() {
        if (isCaller) {
            webRTCManager?.createOffer(isVideoCall)
        }
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
```

---

## Chunk 7: 完善通话界面

### Task 7.1: 更新CallScreen集成WebRTC

**Files:**
- Modify: `android/app/src/main/java/com/videochat/ui/screens/CallScreen.kt`

- [ ] **Step 1: 更新CallScreen.kt**

```kotlin
package com.videochat.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.videochat.ui.viewmodel.CallState
import com.videochat.ui.viewmodel.CallViewModel
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallScreen(
    callId: Long,
    isVideo: Boolean,
    isCaller: Boolean,
    remoteUserId: Long,
    onEndCall: () -> Unit,
    viewModel: CallViewModel = viewModel()
) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }
    val callState by viewModel.callState.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerOn by viewModel.isSpeakerOn.collectAsState()
    val isCameraOn by viewModel.isCameraOn.collectAsState()

    // 初始化通话
    LaunchedEffect(callId) {
        viewModel.initializeCall(callId, remoteUserId, isVideo, isCaller)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (isVideo) {
            permissions.add(Manifest.permission.CAMERA)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            hasPermissions = true
        } else {
            permissionLauncher.launch(notGranted.toTypedArray())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isVideo) "视频通话" else "语音通话") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            when (callState) {
                is CallState.Idle -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                is CallState.Calling -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("等待对方接听...")
                    }
                }

                is CallState.Ringing -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("来电显示")
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            FilledIconButton(
                                onClick = { viewModel.acceptCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    Icons.Default.Call,
                                    contentDescription = "接听",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            FilledIconButton(
                                onClick = { viewModel.rejectCall() },
                                modifier = Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "拒绝",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                is CallState.Connected -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 远程视频或占位符
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isVideo) {
                                // 远程视频渲染占位符
                                // 实际需要通过WebRTC流获取远程视频
                                Text("对方视频")
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(120.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text("通话中...")
                                }
                            }
                        }

                        // 通话控制按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 静音按钮
                            IconButton(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "静音",
                                    modifier = Modifier.size(32.dp),
                                    tint = if (isMuted) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // 扬声器按钮
                            IconButton(
                                onClick = { viewModel.toggleSpeaker() },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                    contentDescription = "扬声器",
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // 摄像头按钮（视频通话）
                            if (isVideo) {
                                IconButton(
                                    onClick = { viewModel.toggleCamera() },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        if (isCameraOn) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = "摄像头",
                                        modifier = Modifier.size(32.dp),
                                        tint = if (!isCameraOn) MaterialTheme.colorScheme.error
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // 切换摄像头按钮
                                IconButton(
                                    onClick = { viewModel.switchCamera() },
                                    modifier = Modifier.size(56.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Cameraswitch,
                                        contentDescription = "切换摄像头",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // 结束通话按钮
                            IconButton(
                                onClick = {
                                    viewModel.endCall()
                                    onEndCall()
                                },
                                modifier = Modifier.size(56.dp)
                            ) {
                                Icon(
                                    Icons.Default.CallEnd,
                                    contentDescription = "结束通话",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }

                is CallState.Ended -> {
                    LaunchedEffect(Unit) {
                        onEndCall()
                    }
                }

                is CallState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "通话错误: ${(callState as CallState.Error).message}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onEndCall) {
                            Text("返回")
                        }
                    }
                }
            }
        }
    }
}
```

---

## 验证步骤

### 验证1: 编译检查
- 运行 `./gradlew assembleDebug` 确认无编译错误
- 确认WebRTC依赖正确加载

### 验证2: 代码完整性检查
- 确认所有文件都已创建
- 确认WebSocket服务能够连接到后端
- 确认WebRTC管理器正确初始化

### 验证3: 功能测试（手动）
- 启动后端服务器
- 运行Android应用
- 测试用户登录
- 测试发起语音通话
- 测试发起视频通话
- 测试接听/拒接通话
- 测试静音/取消静音
- 测试摄像头开关
- 测试结束通话