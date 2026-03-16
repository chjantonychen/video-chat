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
    private val token: String
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
            .url("$baseUrl/ws?token=$token")
            .build()

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                this@WebSocketService.wsListener?.onConnected()
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
                this@WebSocketService.wsListener?.onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Error: ${t.message}")
                this@WebSocketService.wsListener?.onError(t.message ?: "Unknown error")
            }
        })
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
            val message = gson.fromJson(text, Map::class.java)
            val body = message["body"]?.toString() ?: return
            val signal = gson.fromJson(body, SignalMessage::class.java)

            when (signal.type) {
                "call_invite" -> {
                    this.wsListener?.onCallInvite(CallSignal.CallInvite(
                        callId = signal.callId!!,
                        fromUserId = signal.fromUserId!!,
                        callType = signal.callType!!
                    ))
                }
                "call_response" -> {
                    this.wsListener?.onCallResponse(CallSignal.CallResponse(
                        callId = signal.callId!!,
                        accept = signal.accept!!
                    ))
                }
                "offer" -> {
                    this.wsListener?.onOffer(CallSignal.SdpOffer(
                        sdp = signal.sdp!!,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "answer" -> {
                    this.wsListener?.onAnswer(CallSignal.SdpAnswer(
                        sdp = signal.sdp!!,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "ice_candidate" -> {
                    this.wsListener?.onIceCandidate(CallSignal.IceCandidate(
                        candidate = signal.candidate!!,
                        sdpMid = signal.sdpMid,
                        sdpMidIndex = signal.sdpMidIndex,
                        fromUserId = signal.fromUserId!!
                    ))
                }
                "call_end" -> {
                    this.wsListener?.onCallEnd(CallSignal.CallEnd(
                        callId = signal.callId!!
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
}