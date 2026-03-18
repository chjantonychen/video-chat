package com.videochat.webrtc

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebRTC管理器 - 处理P2P音视频通话
 * 使用 org.webrtc:google-webrtc 官方库
 */
class WebRTCManager(private val context: Context) {
    
    private val TAG = "WebRTCManager"
    private val STUN_SERVER = "stun:stun.l.google.com:19302"
    
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var localVideoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var videoSource: VideoSource? = null
    private var eglBase: EglBase? = null
    
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    
    private var onIceCandidateCallback: ((IceCandidate, String) -> Unit)? = null
    private var onRemoteStreamCallback: ((MediaStream) -> Unit)? = null
    private var onIceConnectionStateCallback: ((PeerConnection.IceConnectionState) -> Unit)? = null
    
    @Volatile
    private var localSurfaceView: SurfaceViewRenderer? = null
    @Volatile
    private var remoteSurfaceView: SurfaceViewRenderer? = null
    private var pendingRemoteStream: MediaStream? = null
    
    private var isRemoteDescriptionSet = false
    private var pendingCreateAnswerCallback: ((SessionDescription?) -> Unit)? = null
    
    private val tracksAdded = AtomicBoolean(false)
    
    fun initialize(
        onIceCandidate: (IceCandidate, String) -> Unit,
        onRemoteStream: (MediaStream) -> Unit,
        onIceConnectionState: (PeerConnection.IceConnectionState) -> Unit
    ) {
        this.onIceCandidateCallback = onIceCandidate
        this.onRemoteStreamCallback = onRemoteStream
        this.onIceConnectionStateCallback = onIceConnectionState

        executor.execute {
            try {
                val options = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
                
                PeerConnectionFactory.initialize(options)
                
                val encoderFactory = DefaultVideoEncoderFactory(
                    eglBase?.eglBaseContext,
                    true,
                    true
                )
                val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)
                
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setVideoEncoderFactory(encoderFactory)
                    .setVideoDecoderFactory(decoderFactory)
                    .createPeerConnectionFactory()
                    
                Log.d(TAG, "PeerConnectionFactory initialized")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize PeerConnectionFactory: ${e.message}", e)
            }
        }
    }
    
    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
        }

        val observer = object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    Log.d(TAG, "========== onIceCandidate (local) ==========")
                    Log.d(TAG, "candidate sdp: ${it.sdp}")
                    Log.d(TAG, "candidate sdpMid: ${it.sdpMid}, sdpMLineIndex: ${it.sdpMLineIndex}")
                    onIceCandidateCallback?.invoke(it, it.sdpMid ?: "")
                }
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
                Log.d(TAG, "========== onIceConnectionChange: $state ==========")
                onIceConnectionStateCallback?.invoke(state)
            }

            override fun onSignalingChange(state: PeerConnection.SignalingState) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddStream(stream: MediaStream?) {
                stream?.let {
                    Log.d(TAG, "========== onAddStream ==========")
                    Log.d(TAG, "stream id: ${it.id}, videoTracks: ${it.videoTracks.size}, audioTracks: ${it.audioTracks.size}")
                    
                    if (it.videoTracks.isNotEmpty()) {
                        val videoTrack = it.videoTracks[0] as VideoTrack
                        remoteVideoTrack = videoTrack
                        Log.d(TAG, "Remote video track stored: $remoteVideoTrack")
                        
                        val surfaceView = remoteSurfaceView
                        if (surfaceView != null) {
                            videoTrack.setEnabled(true)
                            localVideoTrack?.removeSink(surfaceView)
                            videoTrack.removeSink(surfaceView)
                            videoTrack.addSink(surfaceView)
                            Log.d(TAG, "Remote video sink added from onAddStream")
                        } else {
                            Log.d(TAG, "Remote surface view not ready, video track saved")
                        }
                    }
                    
                    if (it.audioTracks.isNotEmpty()) {
                        it.audioTracks[0].setEnabled(true)
                    }
                    
                    onRemoteStreamCallback?.invoke(it)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {
                Log.d(TAG, "onRemoveStream: ${stream?.id}")
            }
            
            override fun onTrack(rtpTransceiver: RtpTransceiver?) {
                rtpTransceiver?.let { transceiver ->
                    Log.d(TAG, "========== onTrack ==========")
                    Log.d(TAG, "transceiver mid: ${transceiver.mid}, mediaType: ${transceiver.mediaType}")
                    
                    val receiver = transceiver.receiver
                    val track = receiver.track()
                    Log.d(TAG, "track: $track, kind: ${track?.kind()}, enabled: ${track?.enabled()}")
                    
                    if (track?.kind() == "video") {
                        Log.d(TAG, "========== VIDEO TRACK RECEIVED IN onTrack ==========")
                        handleRemoteVideoTrack(track as VideoTrack)
                    } else if (track?.kind() == "audio") {
                        Log.d(TAG, "Audio track received in onTrack: $track")
                        track.setEnabled(true)
                    }
                }
            }
            
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
    }
    
    private fun ensureTracksAdded(isVideo: Boolean, onComplete: () -> Unit) {
        if (tracksAdded.get()) {
            Log.d(TAG, "Tracks already added, skipping re-add")
            onComplete()
            return
        }
        addTracksInternal(isVideo) {
            tracksAdded.set(true)
            onComplete()
        }
    }
    
    private fun addTracksInternal(isVideo: Boolean, onComplete: () -> Unit) {
        executor.execute {
            try {
                Log.d(TAG, "========== addTracksInternal START ==========")
                Log.d(TAG, "isVideo parameter: $isVideo")
                
                val factory = peerConnectionFactory ?: return@execute

                // 音频
                val audioSource = factory.createAudioSource(MediaConstraints())
                localAudioTrack = factory.createAudioTrack("audio_track", audioSource)

                localAudioTrack?.let { track ->
                    Log.d(TAG, "========== Adding audio track ==========")
                    val sender = peerConnection?.addTrack(track, listOf("stream"))
                    try {
                        peerConnection?.transceivers?.find { it.sender == sender }?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                        Log.d(TAG, "Audio transceiver direction set to SEND_RECV")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not set audio transceiver direction: ${e.message}")
                    }
                }

                if (isVideo) {
                    Log.d(TAG, "========== Setting up video capture ==========")
                    videoCapturer = createVideoCapturer()
                    
                    if (videoCapturer == null) {
                        Log.e(TAG, "========== CAMERA INITIALIZATION FAILED ==========")
                        onComplete()
                        return@execute
                    } else {
                        Log.d(TAG, "========== CAMERA INITIALIZATION SUCCESSFUL ==========")
                        
                        videoSource = factory.createVideoSource(false)
                        Log.d(TAG, "VideoSource created")
                        
                        val surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase?.eglBaseContext)
                        if (surfaceTextureHelper == null) {
                            Log.e(TAG, "Failed to create SurfaceTextureHelper")
                            onComplete()
                            return@execute
                        }
                        
                        videoCapturer?.initialize(surfaceTextureHelper, context, videoSource!!.capturerObserver)
                        videoCapturer?.startCapture(1280, 720, 30)
                        Log.d(TAG, "Video capture started")
                        
                        localVideoTrack = factory.createVideoTrack("video_track", videoSource!!)
                        Log.d(TAG, "VideoTrack created: ${localVideoTrack != null}")

                        localVideoTrack?.let { track ->
                            // DEBUG: 添加本地视频帧计数 sink，确认帧是否产生
                            track.addSink(object : VideoSink {
                                private var frameCount = 0
                                override fun onFrame(frame: VideoFrame?) {
                                    frameCount++
                                    if (frameCount % 30 == 0) {
                                        Log.d(TAG, "Local video frame count: $frameCount")
                                    }
                                }
                            })
                            Log.d(TAG, "Debug sink added to local video track")

                            Log.d(TAG, "========== Adding video track to PeerConnection ==========")
                            val sender = peerConnection?.addTrack(track, listOf("stream"))
                            try {
                                peerConnection?.transceivers?.find { it.sender == sender }?.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                                Log.d(TAG, "Video transceiver direction set to SEND_RECV")
                            } catch (e: Exception) {
                                Log.w(TAG, "Could not set video transceiver direction: ${e.message}")
                            }
                        }
                    }
                }

                Log.d(TAG, "Local tracks added to PeerConnection")
                onComplete()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating local tracks: ${e.message}", e)
                onComplete()
            }
        }
    }
    
    private fun createVideoCapturer(): VideoCapturer? {
        return try {
            Log.d(TAG, "========== createVideoCapturer START ==========")
            val enumerator = Camera1Enumerator(false)
            val deviceNames = enumerator.deviceNames
            Log.d(TAG, "Found ${deviceNames.size} camera devices")
            
            if (deviceNames.isEmpty()) {
                Log.e(TAG, "========== NO CAMERA DEVICES FOUND ==========")
                return null
            }
            
            deviceNames.forEachIndexed { index, deviceName ->
                val isFrontFacing = enumerator.isFrontFacing(deviceName)
                val isBackFacing = enumerator.isBackFacing(deviceName)
                Log.d(TAG, "Camera device $index: '$deviceName', front-facing: $isFrontFacing, back-facing: $isBackFacing")
            }
            
            val deviceName = deviceNames.find { enumerator.isFrontFacing(it) } ?: deviceNames[0]
            Log.d(TAG, "Selected camera device: '$deviceName'")
            
            val capturer = enumerator.createCapturer(deviceName, null)
            
            if (capturer == null) {
                Log.e(TAG, "========== CAMERA CAPTURER CREATION FAILED ==========")
            } else {
                Log.d(TAG, "========== CAMERA CAPTURER CREATED ==========")
            }
            
            capturer
        } catch (e: Exception) {
            Log.e(TAG, "========== EXCEPTION IN createVideoCapturer ==========", e)
            null
        }
    }
    
    fun startLocalVideo(surfaceView: SurfaceViewRenderer) {
        executor.execute {
            try {
                Log.d(TAG, "========== startLocalVideo START ==========")
                Log.d(TAG, "surfaceView hashCode: ${surfaceView.hashCode()}, current localSurfaceView: ${localSurfaceView?.hashCode()}")
                
                val oldSurfaceView = localSurfaceView
                if (oldSurfaceView != null && oldSurfaceView != surfaceView) {
                    localVideoTrack?.removeSink(oldSurfaceView)
                    remoteVideoTrack?.removeSink(oldSurfaceView)
                    Log.d(TAG, "Removed sinks from old SurfaceView")
                }
                
                localSurfaceView = surfaceView
                
                if (surfaceView == remoteSurfaceView) {
                    Log.w(TAG, "WARNING: surfaceView is also the current remoteSurfaceView! Clearing remoteSurfaceView.")
                    remoteSurfaceView = null
                    remoteVideoTrack?.removeSink(surfaceView)
                }
                
                if (eglBase == null) {
                    Log.d(TAG, "Creating new EglBase for local video")
                    eglBase = EglBase.create()
                }
                
                val eglContext = eglBase?.eglBaseContext
                
                mainHandler.post {
                    try {
                        surfaceView.init(eglContext, null)
                        Log.d(TAG, "Local video view initialized on main thread")
                        
                        executor.execute {
                            if (localVideoTrack == null) {
                                Log.e(TAG, "========== localVideoTrack IS NULL ==========")
                                return@execute
                            }
                            
                            remoteVideoTrack?.removeSink(surfaceView)
                            localVideoTrack?.removeSink(surfaceView)
                            localVideoTrack?.addSink(surfaceView)
                            Log.d(TAG, "========== LOCAL VIDEO SINK ADDED ==========")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing local video view: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting local video: ${e.message}", e)
            }
        }
    }
    
    fun setRemoteView(surfaceView: SurfaceViewRenderer) {
        executor.execute {
            try {
                Log.d(TAG, "========== setRemoteView START ==========")
                
                val oldSurfaceView = remoteSurfaceView
                if (oldSurfaceView != null && oldSurfaceView != surfaceView) {
                    pendingRemoteStream?.let { stream ->
                        if (stream.videoTracks.isNotEmpty()) {
                            stream.videoTracks[0].removeSink(oldSurfaceView)
                        }
                    }
                    remoteVideoTrack?.removeSink(oldSurfaceView)
                    localVideoTrack?.removeSink(oldSurfaceView)
                }
                
                remoteSurfaceView = surfaceView
                
                if (surfaceView == localSurfaceView) {
                    Log.w(TAG, "WARNING: surfaceView is also the current localSurfaceView! Clearing localSurfaceView.")
                    localSurfaceView = null
                    localVideoTrack?.removeSink(surfaceView)
                }
                
                if (eglBase == null) {
                    Log.d(TAG, "Creating new EglBase for remote video")
                    eglBase = EglBase.create()
                }
                
                val eglContext = eglBase?.eglBaseContext
                
                mainHandler.post {
                    try {
                        surfaceView.init(eglContext, null)
                        Log.d(TAG, "Remote video view initialized on main thread")
                        
                        executor.execute {
                            remoteVideoTrack?.let { track ->
                                Log.d(TAG, "========== REMOTE VIDEO TRACK FOUND ==========")
                                track.setEnabled(true)
                                localVideoTrack?.removeSink(surfaceView)
                                track.removeSink(surfaceView)
                                track.addSink(surfaceView)
                                Log.d(TAG, "========== REMOTE VIDEO SINK ADDED ==========")
                            } ?: Log.d(TAG, "No remote video track available yet - will be attached when received")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error initializing remote video view: ${e.message}", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error setting remote view: ${e.message}", e)
            }
        }
    }
    
    private fun handleRemoteVideoTrack(videoTrack: VideoTrack) {
        Log.d(TAG, "========== handleRemoteVideoTrack ==========")
        remoteVideoTrack = videoTrack
        
        val surfaceView = remoteSurfaceView
        if (surfaceView != null) {
            Log.d(TAG, "Remote surface view ready, adding sink")
            videoTrack.setEnabled(true)
            localVideoTrack?.removeSink(surfaceView)
            videoTrack.removeSink(surfaceView)
            videoTrack.addSink(surfaceView)
            Log.d(TAG, "========== REMOTE VIDEO SINK ADDED ==========")
        } else {
            Log.d(TAG, "Remote surface view not ready, track saved")
        }
    }
    
    fun createOffer(isVideoCall: Boolean, callback: (SessionDescription?) -> Unit) {
        executor.execute {
            try {
                Log.d(TAG, "========== createOffer START ==========")
                Log.d(TAG, "isVideoCall=$isVideoCall")
                
                if (peerConnection == null) {
                    createPeerConnection()
                }
                
                ensureTracksAdded(isVideoCall) {
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideoCall.toString()))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                    }
                    
                    peerConnection?.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            Log.d(TAG, "Offer created successfully")
                            
                            sdp?.let {
                                val hasVideo = it.description.contains("video")
                                Log.d(TAG, "Offer SDP contains video: $hasVideo")
                                
                                peerConnection?.setLocalDescription(object : SdpObserver {
                                    override fun onCreateSuccess(p0: SessionDescription?) {}
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "Local description set (offer) SUCCESS")
                                        callback(sdp)
                                    }
                                    override fun onCreateFailure(error: String?) {
                                        Log.e(TAG, "Failed to set local description")
                                        callback(null)
                                    }
                                    override fun onSetFailure(error: String?) {
                                        Log.e(TAG, "Failed to set local description")
                                        callback(null)
                                    }
                                }, sdp)
                            } ?: callback(null)
                        }
                        
                        override fun onSetSuccess() {}
                        
                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Failed to create offer: $error")
                            callback(null)
                        }
                        
                        override fun onSetFailure(error: String?) {
                            Log.e(TAG, "Failed to create offer: $error")
                            callback(null)
                        }
                    }, constraints)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error creating offer: ${e.message}", e)
                callback(null)
            }
        }
    }
    
    fun createAnswer(isVideoCall: Boolean, callback: (SessionDescription?) -> Unit) {
        executor.execute {
            // ========== 修复：确保 peerConnection 已创建，否则轨道无法添加 ==========
            if (peerConnection == null) {
                createPeerConnection()
            }
            // ================================================================

            ensureTracksAdded(isVideoCall) {
                if (isRemoteDescriptionSet) {
                    Log.d(TAG, "Remote description already set, creating answer directly")
                    createAnswerInternal(callback)
                } else {
                    Log.d(TAG, "Remote description not set yet, queuing answer creation")
                    pendingCreateAnswerCallback = callback
                }
            }
        }
    }
    
    private fun createAnswerInternal(callback: (SessionDescription?) -> Unit) {
        executor.execute {
            try {
                Log.d(TAG, "========== createAnswerInternal START ==========")

                // FIX: 强制设置视频收发器方向为 SEND_RECV
                peerConnection?.transceivers?.forEach { transceiver ->
                    if (transceiver.mediaType == MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO) {
                        if (transceiver.sender?.track() != null) {
                            transceiver.direction = RtpTransceiver.RtpTransceiverDirection.SEND_RECV
                            Log.d(TAG, "Forced video transceiver direction to SEND_RECV before createAnswer")
                        } else {
                            Log.w(TAG, "Video transceiver has no sender track, direction may be incorrect")
                        }
                    }
                }

                val constraints = MediaConstraints().apply {
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
                    mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                }

                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        Log.d(TAG, "========== createAnswerInternal onCreateSuccess ==========")
                        sdp?.let {
                            // 打印 Answer 中的视频方向属性
                            val directionLines = it.description.lines()
                                .filter { line -> line.startsWith("a=") && (line.contains("sendrecv") || line.contains("sendonly") || line.contains("recvonly") || line.contains("inactive")) }
                            val videoDirection = directionLines.filter { line -> it.description.contains("m=video") && line.startsWith("a=") && (line.contains("sendrecv") || line.contains("sendonly") || line.contains("recvonly") || line.contains("inactive")) }
                            Log.d(TAG, "Answer video direction: ${videoDirection.joinToString()}")
                            
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(p0: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "Local description set (answer) SUCCESS")
                                    callback(sdp)
                                }
                                override fun onCreateFailure(error: String?) {
                                    Log.e(TAG, "onCreateFailure: $error")
                                    callback(null)
                                }
                                override fun onSetFailure(error: String?) {
                                    Log.e(TAG, "Failed to set local description: $error")
                                    callback(null)
                                }
                            }, sdp)
                        } ?: callback(null)
                    }
                    
                    override fun onSetSuccess() {}
                    
                    override fun onCreateFailure(error: String?) {
                        Log.e(TAG, "Failed to create answer: $error")
                        callback(null)
                    }
                    
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "onSetFailure in createAnswer: $error")
                        callback(null)
                    }
                }, constraints)
                
            } catch (e: Exception) {
                Log.e(TAG, "Exception in createAnswer: ${e.message}", e)
                callback(null)
            }
        }
    }
    
    fun setRemoteDescription(sdp: String, type: String, callback: (Boolean) -> Unit) {
        executor.execute {
            try {
                Log.d(TAG, "========== setRemoteDescription START ==========")
                Log.d(TAG, "type=$type, sdp length=${sdp.length}")
                
                if (peerConnection == null) {
                    createPeerConnection()
                }
                
                val hasVideo = sdp.contains("m=video")
                Log.d(TAG, "Remote SDP contains video: $hasVideo")
                
                val sessionDescription = SessionDescription(SessionDescription.Type.fromCanonicalForm(type), sdp)
                
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Remote description set successfully")
                        isRemoteDescriptionSet = true
                        
                        pendingCreateAnswerCallback?.let { answerCallback ->
                            Log.d(TAG, "Executing pending createAnswer callback")
                            createAnswerInternal(answerCallback)
                            pendingCreateAnswerCallback = null
                        }
                        
                        callback(true)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set remote description: $error")
                        callback(false)
                    }
                }, sessionDescription)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting remote description: ${e.message}")
                callback(false)
            }
        }
    }
    
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int) {
        executor.execute {
            try {
                Log.d(TAG, "========== addIceCandidate (remote) ==========")
                val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex, candidate)
                peerConnection?.addIceCandidate(iceCandidate)
                Log.d(TAG, "ICE candidate added successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding ICE candidate: ${e.message}")
            }
        }
    }
    
    fun getLocalDescription(): String? = peerConnection?.localDescription?.description
    
    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }
    
    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        if (!enabled) {
            try {
                videoCapturer?.stopCapture()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping capture: ${e.message}")
            }
        } else {
            try {
                videoCapturer?.startCapture(1280, 720, 30)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting capture: ${e.message}")
            }
        }
    }
    
    fun switchCamera() {
        try {
            (videoCapturer as? Camera2Capturer)?.switchCamera(null)
        } catch (e: Exception) {
            Log.e(TAG, "Switch camera error: ${e.message}")
        }
    }
    
    fun resetViews() {
        executor.execute {
            Log.d(TAG, "========== resetViews ==========")
            localVideoTrack?.removeSink(localSurfaceView)
            remoteVideoTrack?.removeSink(remoteSurfaceView)
            localSurfaceView = null
            remoteSurfaceView = null
            Log.d(TAG, "Views reset completed")
        }
    }
    
    fun release() {
        executor.execute {
            try {
                videoCapturer?.stopCapture()
                videoCapturer = null
                
                localVideoTrack?.removeSink(localSurfaceView)
                localSurfaceView?.release()
                localSurfaceView = null
                
                remoteSurfaceView?.release()
                remoteSurfaceView = null
                
                peerConnection?.close()
                peerConnection = null
                
                peerConnectionFactory?.dispose()
                peerConnectionFactory = null
                
                eglBase?.release()
                eglBase = null
                
                localAudioTrack?.setEnabled(false)
                localAudioTrack = null
                localVideoTrack?.setEnabled(false)
                localVideoTrack = null
                
                remoteVideoTrack?.setEnabled(false)
                remoteVideoTrack?.removeSink(remoteSurfaceView)
                remoteVideoTrack = null
                
                pendingRemoteStream = null
                isRemoteDescriptionSet = false
                pendingCreateAnswerCallback = null
                tracksAdded.set(false)
                
                Log.d(TAG, "WebRTC released")
            } catch (e: Exception) {
                Log.e(TAG, "Release error: ${e.message}")
            }
        }
    }
}