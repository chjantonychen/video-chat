package com.videochat.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("friend")
public class Friend {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long friendId;
    private Integer status;
    private LocalDateTime createdAt;
}
