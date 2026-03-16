package com.videochat.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("friend_request")
public class FriendRequest {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private Integer status;
    private LocalDateTime createdAt;
}
