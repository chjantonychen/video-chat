package com.videochat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.Message;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {
    
    @Insert("INSERT INTO message (from_user_id, to_user_id, type, content, is_read, created_at) " +
            "VALUES (#{fromUserId}, #{toUserId}, #{type}, #{content}, #{isRead}, #{createdAt})")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyColumn = "id", keyProperty = "id", before = false, resultType = Long.class)
    int insertWithId(Message message);
}
