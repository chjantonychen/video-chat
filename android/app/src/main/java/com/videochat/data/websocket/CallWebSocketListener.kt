package com.videochat.data.websocket

import com.videochat.data.model.CallSignal

interface CallWebSocketListener {
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