package com.videochat.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("call_record")
public class CallRecord {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long callerId;
    private Long calleeId;
    private Integer type;
    private Integer status;
    private Integer duration;
    private LocalDateTime createdAt;
}
