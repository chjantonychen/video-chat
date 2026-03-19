package com.videochat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.Friend;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectKey;

@Mapper
public interface FriendMapper extends BaseMapper<Friend> {
    
    @Insert("INSERT INTO friend (user_id, friend_id, status, created_at) " +
            "VALUES (#{userId}, #{friendId}, #{status}, #{createdAt})")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyColumn = "id", keyProperty = "id", before = false, resultType = Long.class)
    int insertWithId(Friend friend);
}
