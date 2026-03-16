package com.videochat.service;

import com.videochat.model.CallRecord;
import java.util.List;

public interface CallService {
    CallRecord createCallRecord(Long callerId, Long calleeId, Integer type);
    void updateCallStatus(Long callId, Integer status);
    CallRecord getCallRecord(Long callId);
    void endCall(Long callId);
    List<CallRecord> getCallHistory(Long userId, Integer page, Integer size);
}
