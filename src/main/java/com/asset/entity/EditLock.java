package com.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edit_lock")
public class EditLock {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long assetFileId;

    private String lockTicket;

    private Long lockedBy;

    private LocalDateTime lockedAt;

    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;
}
