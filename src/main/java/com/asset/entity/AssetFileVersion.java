package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("asset_file_version")
public class AssetFileVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long assetNodeId;
    private String versionNo;
    private String filePath;
    private String solrDocId;
    private Long uploadBy;
    private LocalDateTime uploadTime;
}
