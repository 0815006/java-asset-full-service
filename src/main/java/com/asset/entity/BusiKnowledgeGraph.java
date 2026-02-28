package com.asset.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("busi_knowledge_graph")
public class BusiKnowledgeGraph {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sourceProductId;
    private Long targetProductId;
    private String relationType;
    private LocalDateTime createdAt;
}
