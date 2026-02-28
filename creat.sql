-- ==========================================================
-- 测试资产库系统 (V2.0) 数据库初始化脚本
-- 数据库类型: TDSQL / MySQL (InnoDB)
-- 字符集: utf8mb4
-- ==========================================================

-- ----------------------------
-- 1. 字典与用户域
-- ----------------------------
 
use dabatase asset_db;

-- 团队字典表
CREATE TABLE `sys_team` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '团队名称 (如: 测试一团队)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='团队字典表';

-- 业务领域字典表
CREATE TABLE `sys_domain` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(100) NOT NULL COMMENT '领域名称 (如: 核心业务系统)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务领域字典表';

-- 用户表
CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名/姓名 (如: 陈东)',
  `role` VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT '角色: ADMIN(管理员)/MANAGER(产品经理)/USER(普通用户)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';


-- ----------------------------
-- 2. 产品业务域
-- ----------------------------

-- 产品信息表
CREATE TABLE `busi_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(200) NOT NULL COMMENT '产品名称',
  `team_id` BIGINT NOT NULL COMMENT '所属团队ID (关联 sys_team.id)',
  `domain_id` BIGINT NOT NULL COMMENT '所属领域ID (关联 sys_domain.id)',
  `owner_id` BIGINT COMMENT '产品/测试负责人ID (关联 sys_user.id)',
  `asset_count` INT DEFAULT 0 COMMENT '资产总数 (可由定时任务或触发器更新)',
  `status` VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE(活跃)/ARCHIVED(归档)',
  `last_update` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最新更新时间',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_team_id` (`team_id`),
  KEY `idx_domain_id` (`domain_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品信息表';

-- 用户产品收藏表 (黄星关注功能)
CREATE TABLE `busi_user_favorite` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID (关联 sys_user.id)',
  `product_id` BIGINT NOT NULL COMMENT '收藏的产品ID (关联 busi_product.id)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`) COMMENT '联合唯一索引: 防止重复收藏'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户产品收藏表';

-- 产品知识图谱关系表
CREATE TABLE `busi_knowledge_graph` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `source_product_id` BIGINT NOT NULL COMMENT '源产品ID',
  `target_product_id` BIGINT NOT NULL COMMENT '目标产品ID',
  `relation_type` VARCHAR(50) COMMENT '关系类型 (如: 依赖、调用、关联)',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_source` (`source_product_id`),
  KEY `idx_target` (`target_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品知识图谱关系表';


-- ----------------------------
-- 3. 资产核心域 (树形结构 + 版本控制)
-- ----------------------------

-- 资产节点表 (核心表：统一存储目录与文件)
CREATE TABLE `asset_node` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` VARCHAR(255) NOT NULL COMMENT '节点名称 (文件夹名或文件名)',
  `type` VARCHAR(20) NOT NULL COMMENT '节点类型: folder(目录) / file(文件)',
  `parent_id` BIGINT DEFAULT 0 COMMENT '父节点ID (0或null表示顶级节点)',
  `zone_type` VARCHAR(20) NOT NULL COMMENT '所属专区: tech(技术)/management(管理)/product(产品)',
  `product_id` BIGINT COMMENT '关联产品ID (仅 zone_type=product 时有值)',
  `is_fixed` TINYINT(1) DEFAULT 0 COMMENT '是否固定目录: 1-是(不可删改), 0-否(允许删改)',
  `sort_order` INT DEFAULT 0 COMMENT '排序权重 (用于控制目录显示顺序)',
  `file_path` VARCHAR(500) COMMENT '服务器本地磁盘物理隔离路径 (仅文件有值)',
  `file_extension` VARCHAR(20) COMMENT '文件后缀 (如 pdf, docx, xlsx)',
  `file_size` BIGINT DEFAULT 0 COMMENT '文件大小 (单位: KB)',
  `current_version` VARCHAR(50) COMMENT '当前版本号 (如 V1.0)',
  `solr_doc_id` VARCHAR(100) COMMENT '关联的Solr索引文档ID (用于同步删除/更新)',
  `created_by` BIGINT COMMENT '创建人/上传人ID',
  `updated_by` BIGINT COMMENT '最后修改人ID',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_zone_type` (`zone_type`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资产节点表(目录与文件统一存储)';

-- 文件历史版本表 (同名文件覆盖/版本递增)
CREATE TABLE `asset_file_version` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `asset_node_id` BIGINT NOT NULL COMMENT '关联的资产节点ID (关联 asset_node.id)',
  `version_no` VARCHAR(50) NOT NULL COMMENT '版本号 (如 V1.0, V2.0)',
  `file_path` VARCHAR(500) NOT NULL COMMENT '该历史版本的物理路径',
  `solr_doc_id` VARCHAR(100) COMMENT '该历史版本在Solr的记录ID',
  `upload_by` BIGINT COMMENT '上传人ID',
  `upload_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`),
  KEY `idx_asset_node_id` (`asset_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件历史版本表';
