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
        // Try to find by callId first
        CallRecord record = callRecordMapper.selectById(callId);
        if (record == null && callId != null && callId > 0) {
            // If callId is not valid, try to find the most recent active call
            // This handles the case where caller doesn't know the callId
            LambdaQueryWrapper<CallRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(CallRecord::getStatus, 0) // status 0 = ongoing
                   .orderByDesc(CallRecord::getCreatedAt)
                   .last("LIMIT 1");
            record = callRecordMapper.selectOne(wrapper);
        }
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
