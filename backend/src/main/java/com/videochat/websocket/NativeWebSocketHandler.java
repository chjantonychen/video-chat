package com.videochat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videochat.model.CallRecord;
import com.videochat.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class NativeWebSocketHandler extends TextWebSocketHandler {

    private final CallService callService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 存储所有WebSocket sessions
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    // 存储session和userId的映射
    private final Map<String, Long> sessionUserMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("========== WebSocket Connected ==========");
        log.info("SessionId: {}, RemoteAddress: {}", session.getId(), session.getRemoteAddress());
        sessions.put(session.getId(), session);
        log.info("Current sessions map: {}", sessions.keySet());
    }

@Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.info("Received WebSocket message: {}", payload);
            Map<String, Object> data = objectMapper.readValue(payload, Map.class);
            String destination = (String) data.get("destination");
            String body = (String) data.get("body");
            log.info("destination={}, body={}", destination, body);

            if (body != null) {
                Map<String, Object> bodyMap = objectMapper.readValue(body, Map.class);
                handleMessage(destination, bodyMap, session);
            } else {
                log.warn("body is null, message: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage(), e);
        }
    }

    private void handleMessage(String destination, Map<String, Object> data, WebSocketSession session) {
        switch (destination) {
            case "/app/call/invite":
                handleCallInvite(data);
                break;
            case "/app/call/response":
                handleCallResponse(data);
                break;
            case "/app/call/offer":
                handleOffer(data);
                break;
            case "/app/call/answer":
                handleAnswer(data);
                break;
            case "/app/call/ice":
                handleIceCandidate(data);
                break;
            case "/app/call/end":
                handleCallEnd(data);
                break;
            case "/app/auth":
                // 处理认证，存储userId
                Long userId = Long.parseLong(data.get("userId").toString());
                sessionUserMap.put(session.getId(), userId);
                session.getAttributes().put("userId", userId);
                log.info("========== User Authenticated ==========");
                log.info("SessionId: {}, userId: {}", session.getId(), userId);
                log.info("Updated sessionUserMap: {}", sessionUserMap);
                break;
            default:
                log.warn("Unknown destination: {}", destination);
        }
    }

    private void handleCallInvite(Map<String, Object> data) {
        Long fromUserId = Long.parseLong(data.get("fromUserId").toString());
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        Integer callType = Integer.parseInt(data.get("type").toString());
        
        log.info("handleCallInvite: from={}, to={}, type={}, sessions.size={}", fromUserId, toUserId, callType, sessions.size());
        
        // 先创建通话记录，获取真正的callId
        CallRecord record = null;
        Long realCallId = null;
        try {
            record = callService.createCallRecord(fromUserId, toUserId, callType);
            realCallId = record.getId();
            log.info("Created call record with realCallId={}", realCallId);
        } catch (Exception e) {
            log.error("Failed to create call record: {}", e.getMessage());
            // 如果创建失败，使用临时ID
            realCallId = System.currentTimeMillis();
        }
        
        // 发送 call_invite 给被叫方
        sendToUser(toUserId, Map.of(
            "type", "call_invite",
            "callId", realCallId,
            "fromUserId", fromUserId,
            "callType", callType
        ));
        
        // 发送 call_invite_confirm 给主叫方，包含真正的callId
        sendToUser(fromUserId, Map.of(
            "type", "call_invite_confirm",
            "callId", realCallId,
            "toUserId", toUserId,
            "callType", callType
        ));
        
        log.info("handleCallInvite: sent call_invite to {} and call_invite_confirm to {}", toUserId, fromUserId);
    }

    private void handleCallResponse(Map<String, Object> data) {
        Long callId = Long.parseLong(data.get("callId").toString());
        Boolean accept = Boolean.parseBoolean(data.get("accept").toString());
        
        log.info("========== handleCallResponse START ==========");
        log.info("callId={}, accept={}", callId, accept);
        
        // 更新通话状态
        try {
            callService.updateCallStatus(callId, accept ? 1 : 2);
        } catch (Exception e) {
            log.error("Failed to update call status: {}", e.getMessage());
        }

        // 查询通话记录，获取callerId和calleeId
        CallRecord record = null;
        Long callerId = null;
        Long calleeId = null;
        
        try {
            record = callService.getCallRecord(callId);
            if (record != null) {
                callerId = record.getCallerId();
                calleeId = record.getCalleeId();
            } else {
                log.error("CallRecord not found for callId={}", callId);
            }
        } catch (Exception e) {
            log.error("Failed to get call record: {}", e.getMessage());
        }
        
        if (callerId == null) {
            log.error("callerId is null, cannot send call_response");
            return;
        }
        
        log.info("CallRecord: callerId={}, calleeId={}", callerId, calleeId);
        
        // 打印所有在线session
        log.info("All sessions: {}", sessionUserMap);

        // 发送 call_response 给主叫方，包含 toUserId（被叫方ID）
        sendToUser(callerId, Map.of(
            "type", "call_response",
            "callId", callId,
            "accept", accept,
            "toUserId", calleeId != null ? calleeId : 0
        ));
        
        log.info("========== handleCallResponse END ==========");
    }

    private void handleOffer(Map<String, Object> data) {
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        Long fromUserId = Long.parseLong(data.get("fromUserId").toString());
        
        sendToUser(toUserId, Map.of(
            "type", "offer",
            "sdp", data.get("sdp"),
            "fromUserId", fromUserId
        ));
    }

    private void handleAnswer(Map<String, Object> data) {
        log.info("========== handleAnswer START ==========");
        log.info("data: {}", data);
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        Long fromUserId = Long.parseLong(data.get("fromUserId").toString());
        
        sendToUser(toUserId, Map.of(
            "type", "answer",
            "sdp", data.get("sdp"),
            "fromUserId", fromUserId
        ));
        log.info("========== handleAnswer END ==========");
    }

    private void handleIceCandidate(Map<String, Object> data) {
        Long toUserId = Long.parseLong(data.get("toUserId").toString());
        Long fromUserId = Long.parseLong(data.get("fromUserId").toString());
        
        sendToUser(toUserId, Map.of(
            "type", "ice_candidate",
            "candidate", data.get("candidate"),
            "sdpMid", data.get("sdpMid") != null ? data.get("sdpMid") : "",
            "sdpMidIndex", data.get("sdpMidIndex") != null ? data.get("sdpMidIndex") : 0,
            "fromUserId", fromUserId
        ));
    }

    private void handleCallEnd(Map<String, Object> data) {
        Long callId = Long.parseLong(data.get("callId").toString());
        Long toUserId = Long.parseLong(data.get("toUserId").toString());

        callService.endCall(callId);

        sendToUser(toUserId, Map.of(
            "type", "call_end",
            "callId", callId
        ));
    }

    private void sendToUser(Long userId, Map<String, Object> message) {
        try {
            // 创建可变Map用于添加targetUserId
            Map<String, Object> mutableMessage = new java.util.HashMap<>(message);
            mutableMessage.put("targetUserId", userId);
            String json = objectMapper.writeValueAsString(mutableMessage);
            TextMessage textMessage = new TextMessage(json);

            log.info("========== sendToUser START ==========");
            log.info("Sending to userId={}, type={}, full message: {}", userId, mutableMessage.get("type"), json);
            log.info("All sessionUserMap entries: {}", sessionUserMap);
            
            // 找到对应用户的所有session
            List<String> targetSessionIds = new java.util.ArrayList<>();
            for (Map.Entry<String, Long> entry : sessionUserMap.entrySet()) {
                if (entry.getValue().equals(userId)) {
                    targetSessionIds.add(entry.getKey());
                    log.info("Found session {} for userId {}", entry.getKey(), userId);
                }
            }
            
            if (targetSessionIds.isEmpty()) {
                log.warn("No session found for userId={}! sessionUserMap={}", userId, sessionUserMap);
                return;
            }
            
            log.info("Found {} sessions for userId {}: {}", targetSessionIds.size(), userId, targetSessionIds);
            
            // 发送给该用户的所有session
            int sentCount = 0;
            for (String targetSessionId : targetSessionIds) {
                WebSocketSession targetSession = sessions.get(targetSessionId);
                if (targetSession != null && targetSession.isOpen()) {
                    targetSession.sendMessage(textMessage);
                    log.info("SUCCESS: Sent to session {} for userId {}", targetSessionId, userId);
                    sentCount++;
                } else {
                    log.warn("FAILED: Session {} is not open for userId {} (session exists: {})", 
                        targetSessionId, userId, targetSession != null);
                }
            }
            
            log.info("sendToUser END: sent to {}/{} sessions", sentCount, targetSessionIds.size());
            log.info("========== sendToUser END ==========");
        } catch (Exception e) {
            log.error("Error sending message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        sessionUserMap.remove(session.getId());
        log.info("Native WebSocket disconnected: {}", session.getId());
    }
}