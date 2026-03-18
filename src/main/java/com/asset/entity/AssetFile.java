package com.asset.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_file")
public class AssetFile {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;

    private Long parentId;

    private String treePath;

    /**
     * 1=文件夹, 2=文件
     */
    private Integer nodeType;

    private String fileName;

    private String ext;

    private Long fileSize;

    private Integer versionNo;

    private Integer isLatest;

    private String localPath;

    private String pdfPath;

    private String solrId;

    /**
     * 0=无需, 1=排队, 2=解析中, 3=成功, 4=失败
     */
    private Integer parseStatus;

    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    // Transient fields
    @TableField(exist = false)
    private Boolean hasChildren;

    @TableField(exist = false)
    private Integer subFolderFlag; // 1=有子文件夹, 0=无子文件夹

    @TableField(exist = false)
    private Object currentUserPermission;
    
    @TableField(exist = false)
    private String createdByName;

    @TableField(exist = false)
    private Boolean isNew; // 是否为新文件（根据用户阅读状态判断）

    @TableField(exist = false)
    private Boolean isStarred; // 是否已收藏

    @TableField(exist = false)
    private Boolean isPinned; // 是否已置顶
}
