package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_curated")
public class AssetCurated {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long productId;
    private Long fileId;
    private String reason;
    private Integer displayOrder;
    private LocalDateTime createdAt;
}
