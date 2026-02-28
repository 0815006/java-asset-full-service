package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("busi_product")
public class BusiProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long teamId;
    private Long domainId;
    private Long ownerId;
    private Integer assetCount;
    private String status;
    private LocalDateTime lastUpdate;
    private LocalDateTime createdAt;
}
