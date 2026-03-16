package com.videochat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.FriendRequest;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;

@Mapper
public interface FriendRequestMapper extends BaseMapper<FriendRequest> {
    
    @Insert("INSERT INTO friend_request (from_user_id, to_user_id, status, created_at) " +
            "VALUES (#{fromUserId}, #{toUserId}, #{status}, #{createdAt})")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyColumn = "id", keyProperty = "id", before = false, resultType = Long.class)
    int insertWithId(FriendRequest request);
}
