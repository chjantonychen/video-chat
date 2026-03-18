package com.videochat.data.websocket

import android.util.Log
import com.google.gson.Gson
import com.videochat.data.model.CallSignal
import com.videochat.data.model.SignalMessage
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketService(
    private val baseUrl: String,
    private val token: String,
    private val currentUserId: Long = 0
) {
    companion object {
        private const val TAG = "WebSocketService"
    }

    private var webSocket: WebSocket? = null
    private var wsListener: CallWebSocketListener? = null
    private val gson = Gson()

    private val okHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    fun connect(listener: CallWebSocketListener) {
        this.wsListener = listener
        val request = Request.Builder()
            .url("$baseUrl/ws-native?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "========== WebSocket onOpen ==========")
                Log.d(TAG, "WebSocket connected, currentUserId=$currentUserId, sending auth with userId=$currentUserId")
                // 发送认证消息，让服务器知道这个连接属于哪个用户
                sendAuth(currentUserId)
                this@WebSocketService.wsListener?.onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "========== onMessage RECEIVED ==========")
                Log.d(TAG, "WS received: $text")
                parseMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Closed: $code $reason")
                this@WebSocketService.wsListener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: ${t.message}")
                this@WebSocketService.wsListener?.onError(t.message ?: "Unknown error")
            }
        })
    }

    private fun sendAuth(userId: Long) {
        val message = mapOf(
            "destination" to "/app/auth",
            "body" to "{\"userId\":\"$userId\"}"
        )
        val json = gson.toJson(message)
        Log.d(TAG, "Sending auth: $json")
        webSocket?.send(json)
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        webSocket = null
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
        android.util.Log.d("WebSocketService", "========== sendOffer ==========")
        android.util.Log.d("WebSocketService", "toUserId=$toUserId, sdp length=${sdp.length}")
        val message = mapOf(
            "toUserId" to toUserId.toString(),
            "sdp" to sdp,
            "fromUserId" to fromUserId.toString()
        )
        sendMessage("/app/call/offer", message)
    }

    fun sendAnswer(toUserId: Long, sdp: String, fromUserId: Long) {
        android.util.Log.d("WebSocketService", "========== sendAnswer ==========")
        android.util.Log.d("WebSocketService", "toUserId=$toUserId, sdp length=${sdp.length}")
        val message = mapOf(
            "toUserId" to toUserId.toString(),
            "sdp" to sdp,
            "fromUserId" to fromUserId.toString()
        )
        sendMessage("/app/call/answer", message)
    }

    fun sendIceCandidate(toUserId: Long, candidate: String, sdpMid: String?, sdpMidIndex: Int?, fromUserId: Long) {
        android.util.Log.d("WebSocketService", "========== sendIceCandidate ==========")
        android.util.Log.d("WebSocketService", "toUserId=$toUserId, candidate=$candidate")
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
        val json = gson.toJson(fullMessage)
        Log.d(TAG, "Sending: $json")
        webSocket?.send(json)
    }

    private fun parseMessage(text: String) {
        try {
            Log.d(TAG, "========== parseMessage START ==========")
            Log.d(TAG, "Raw message: $text")
            Log.d(TAG, "currentUserId: $currentUserId")
            
            val message = gson.fromJson(text, Map::class.java)
            
            // 支持两种格式：1. STOMP格式 {"body": "..."} 2. 直接JSON格式 {"type": "..."}
            val signal = if (message.containsKey("body")) {
                val body = message["body"]?.toString() ?: run {
                    Log.w(TAG, "body is null, skipping message")
                    return
                }
                Log.d(TAG, "Parsed STOMP format, body: $body")
                gson.fromJson(body, SignalMessage::class.java)
            } else {
                // 直接消息格式（来自原生WebSocket）
                Log.d(TAG, "Parsed direct JSON format")
                gson.fromJson(text, SignalMessage::class.java)
            }
            
            Log.d(TAG, "Parsed signal: type=${signal.type}, targetUserId=${signal.targetUserId}, fromUserId=${signal.fromUserId}")
            
            // 过滤：如果消息不是发给我的，忽略
            val targetUserId = signal.targetUserId?.toString()?.toLongOrNull()
            Log.d(TAG, "Filtering check: targetUserId=$targetUserId, currentUserId=$currentUserId")
            
            if (targetUserId != null && targetUserId != currentUserId) {
                Log.w(TAG, "========== MESSAGE FILTERED (not for me) ==========")
                Log.w(TAG, "Ignoring message for other user: targetUserId=$targetUserId, currentUserId=$currentUserId")
                return
            }
            
            Log.d(TAG, "MESSAGE PASSED FILTER - processing...")
            Log.d(TAG, "========== parseMessage END ==========")
            
            // 必须在主线程调用listener
            val listener = this.wsListener
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val sig = signal
            
            handler.post {
                try {
                    when (sig.type) {
                        "call_invite" -> {
                            listener?.onCallInvite(CallSignal.CallInvite(
                                callId = sig.callId!!,
                                fromUserId = sig.fromUserId!!,
                                callType = sig.callType!!
                            ))
                        }
                        "call_invite_confirm" -> {
                            listener?.onCallInviteConfirm(CallSignal.CallInviteConfirm(
                                callId = sig.callId!!,
                                toUserId = sig.toUserId!!,
                                callType = sig.callType!!
                            ))
                        }
                        "call_response" -> {
                            listener?.onCallResponse(CallSignal.CallResponse(
                                callId = sig.callId!!,
                                accept = sig.accept!!,
                                toUserId = sig.toUserId
                            ))
                        }
                        "offer" -> {
                            listener?.onOffer(CallSignal.SdpOffer(
                                sdp = sig.sdp!!,
                                fromUserId = sig.fromUserId!!
                            ))
                        }
                        "answer" -> {
                            listener?.onAnswer(CallSignal.SdpAnswer(
                                sdp = sig.sdp!!,
                                fromUserId = sig.fromUserId!!
                            ))
                        }
                        "ice_candidate" -> {
                            listener?.onIceCandidate(CallSignal.IceCandidate(
                                candidate = sig.candidate!!,
                                sdpMid = sig.sdpMid,
                                sdpMidIndex = sig.sdpMidIndex,
                                fromUserId = sig.fromUserId!!
                            ))
                        }
                        "call_end" -> {
                            listener?.onCallEnd(CallSignal.CallEnd(
                                callId = sig.callId!!
                            ))
                        }
                        else -> {}
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in listener callback: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}", e)
        }
    }
}
