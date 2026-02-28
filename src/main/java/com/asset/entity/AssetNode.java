package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_node")
public class AssetNode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;
    private Long parentId;
    private String zoneType;
    private Long productId;
    private Boolean isFixed;
    private Integer sortOrder;
    private String filePath;
    private String fileExtension;
    private Long fileSize;
    private String currentVersion;
    private String solrDocId;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
