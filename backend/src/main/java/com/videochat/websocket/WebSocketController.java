package com.videochat.websocket;

import com.videochat.model.CallRecord;
import com.videochat.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final CallService callService;
    
    // Call invitation
    @MessageMapping("/call/invite")
    public void handleCallInvite(@Payload Map<String, Object> data) {
        Long fromUserId = Long.parseLong(data.get("fromUserId").toString());
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        Integer callType = Integer.parseInt(data.get("type").toString());
        
        // Create call record
        CallRecord record = callService.createCallRecord(fromUserId, toUserId, callType);
        
        // Send to callee
        messagingTemplate.convertAndSend("/queue/call/" + toUserId, Map.of(
            "type", "call_invite",
            "callId", record.getId(),
            "fromUserId", fromUserId,
            "callType", callType
        ));
    }
    
    // Call response (accept/reject)
    @MessageMapping("/call/response")
    public void handleCallResponse(@Payload Map<String, Object> data) {
        Long callId = Long.parseLong(data.get("callId").toString());
        Boolean accept = Boolean.parseBoolean(data.get("accept").toString());
        
        callService.updateCallStatus(callId, accept ? 1 : 2);
        
        CallRecord record = callService.getCallRecord(callId);
        Long callerId = record.getCallerId();
        
        messagingTemplate.convertAndSend("/queue/call/" + callerId, Map.of(
            "type", "call_response",
            "callId", callId,
            "accept", accept
        ));
    }
    
    // SDP offer
    @MessageMapping("/call/offer")
    public void handleOffer(@Payload Map<String, Object> data) {
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        
        messagingTemplate.convertAndSend("/queue/call/" + toUserId, Map.of(
            "type", "offer",
            "sdp", data.get("sdp"),
            "fromUserId", data.get("fromUserId")
        ));
    }
    
    // SDP answer
    @MessageMapping("/call/answer")
    public void handleAnswer(@Payload Map<String, Object> data) {
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        
        messagingTemplate.convertAndSend("/queue/call/" + toUserId, Map.of(
            "type", "answer",
            "sdp", data.get("sdp"),
            "fromUserId", data.get("fromUserId")
        ));
    }
    
    // ICE candidate
    @MessageMapping("/call/ice")
    public void handleIceCandidate(@Payload Map<String, Object> data) {
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        
        messagingTemplate.convertAndSend("/queue/call/" + toUserId, Map.of(
            "type", "ice_candidate",
            "candidate", data.get("candidate"),
            "sdpMid", data.get("sdpMid"),
            "sdpMidIndex", data.get("sdpMidIndex"),
            "fromUserId", data.get("fromUserId")
        ));
    }
    
    // Call ended
    @MessageMapping("/call/end")
    public void handleCallEnd(@Payload Map<String, Object> data) {
        Long callId = Long.parseLong(data.get("callId").toString());
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        
        callService.endCall(callId);
        
        messagingTemplate.convertAndSend("/queue/call/" + toUserId, Map.of(
            "type", "call_end",
            "callId", callId
        ));
    }
}
