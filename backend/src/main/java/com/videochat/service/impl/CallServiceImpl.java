package com.videochat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.videochat.mapper.CallRecordMapper;
import com.videochat.model.CallRecord;
import com.videochat.service.CallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {
    
    private final CallRecordMapper callRecordMapper;
    
    @Override
    public CallRecord createCallRecord(Long callerId, Long calleeId, Integer type) {
        CallRecord record = new CallRecord();
        record.setCallerId(callerId);
        record.setCalleeId(calleeId);
        record.setType(type);
        record.setStatus(0);
        record.setDuration(0);
        record.setCreatedAt(LocalDateTime.now());
        callRecordMapper.insert(record);
        return record;
    }
    
    @Override
    public void updateCallStatus(Long callId, Integer status) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record != null) {
            record.setStatus(status);
            callRecordMapper.updateById(record);
        }
    }
    
    @Override
    public CallRecord getCallRecord(Long callId) {
        return callRecordMapper.selectById(callId);
    }
    
    @Override
    public void endCall(Long callId) {
        CallRecord record = callRecordMapper.selectById(callId);
        if (record != null) {
            record.setStatus(1);
            long seconds = java.time.Duration.between(record.getCreatedAt(), LocalDateTime.now()).getSeconds();
            record.setDuration((int) seconds);
            callRecordMapper.updateById(record);
        }
    }
    
    @Override
    public List<CallRecord> getCallHistory(Long userId, Integer page, Integer size) {
        LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(w -> w
            .eq(CallRecord::getCallerId, userId)
            .or()
            .eq(CallRecord::getCalleeId, userId)
        );
        wrapper.orderByDesc(CallRecord::getCreatedAt);
        
        Page<CallRecord> callPage = new Page<>(page, size);
        return callRecordMapper.selectPage(callPage, wrapper).getRecords();
    }
}
