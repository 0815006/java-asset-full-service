package com.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String productName;

    private String productCode;

    private String teamName;

    private String domainName;

    private Long ownerId;

    private Integer assetCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    // Transient fields for frontend display
    @TableField(exist = false)
    private Boolean isFavorited;

    @TableField(exist = false)
    private Object currentUserPermission;
    
    @TableField(exist = false)
    private String ownerName;
}
