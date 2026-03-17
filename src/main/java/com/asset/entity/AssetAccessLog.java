package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("asset_access_log")
public class AssetAccessLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long fileId;
    private Long productId;
    private LocalDateTime createdAt;
}
