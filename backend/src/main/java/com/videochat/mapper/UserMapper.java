package com.videochat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.videochat.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.SelectKey;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    @Insert("INSERT INTO user (username, password, nickname, avatar, signature, created_at, updated_at) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{avatar}, #{signature}, #{createdAt}, #{updatedAt})")
    @SelectKey(statement = "SELECT last_insert_rowid()", keyColumn = "id", keyProperty = "id", before = false, resultType = Long.class)
    int insertWithId(User user);
}
