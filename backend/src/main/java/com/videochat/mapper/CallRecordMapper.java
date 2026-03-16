package com.videochat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.CallRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;

@Mapper
public interface CallRecordMapper extends BaseMapper<CallRecord> {
    
    @Insert("INSERT INTO call_record (caller_id, callee_id, type, status, duration, created_at) " +
            "VALUES (#{callerId}, #{calleeId}, #{type}, #{status}, #{duration}, #{createdAt})")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyColumn = "id", keyProperty = "id", before = false, resultType = Long.class)
    int insertWithId(CallRecord record);
}
