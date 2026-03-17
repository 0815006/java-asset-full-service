package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_file_star")
public class UserFileStar {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileId;
    private Boolean isPinned;
    private Integer pinOrder;
    private LocalDateTime createdAt;
}
