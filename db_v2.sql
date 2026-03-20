-- ---------------------------------------------------------
-- 资产管理系统数据库建表脚本 (Production Ready)
-- 版本: V2.1
-- 字符集: utf8mb4
-- 存储引擎: InnoDB
-- ---------------------------------------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户信息表 (asset_user)
-- ----------------------------
DROP TABLE IF EXISTS `asset_user`;
CREATE TABLE `asset_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `username` VARCHAR(50) NOT NULL COMMENT '登录用户名',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码哈希',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名',
  `emp_no` VARCHAR(20) NOT NULL COMMENT '员工工号',
  `role_type` TINYINT NOT NULL DEFAULT 3 COMMENT '角色类型: 1=管理员, 2=测试经理, 3=普通用户, 4=外购人员',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (1=已删除, 0=正常)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`) COMMENT '用户名唯一索引',
  UNIQUE KEY `uk_emp_no` (`emp_no`) COMMENT '工号唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户信息表';

-- ----------------------------
-- 2. 产品信息表 (asset_product)
-- ----------------------------
DROP TABLE IF EXISTS `asset_product`;
CREATE TABLE `asset_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称',
  `product_code` VARCHAR(50) NOT NULL COMMENT '产品编码',
  `team_name` VARCHAR(100) NOT NULL COMMENT '所属团队',
  `domain_name` VARCHAR(100) NOT NULL COMMENT '所属业务域',
  `owner_id` BIGINT NOT NULL COMMENT '负责人 ID',
  `asset_count` INT NOT NULL DEFAULT 0 COMMENT '资产总数统计 (冗余字段)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (1=已删除, 0=正常)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`) COMMENT '产品编码唯一索引',
  KEY `idx_owner_id` (`owner_id`) COMMENT '负责人索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品信息表';

-- ----------------------------
-- 3. 资产文件表 (asset_file)
-- ----------------------------
DROP TABLE IF EXISTS `asset_file`;
CREATE TABLE `asset_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `product_id` BIGINT NOT NULL COMMENT '所属产品 ID (0 表示公共专区)',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点 ID (0 表示根节点)',
  `tree_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '树形路径 (如 /0/1/5/)',
  `node_type` TINYINT NOT NULL COMMENT '节点类型: 1=文件夹, 2=文件',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件或文件夹名称',
  `ext` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '文件扩展名',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小 (Bytes)',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号',
  `is_latest` TINYINT NOT NULL DEFAULT 1 COMMENT '是否为最新版本 (1=是, 0=否)',
  `local_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '物理存储路径',
  `pdf_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '预览用 PDF 路径',
  `solr_id` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '对应的 Solr 文档 ID',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '解析状态: 0=无需, 1=排队, 2=解析中, 3=成功, 4=失败',
  `created_by` BIGINT NOT NULL COMMENT '创建者 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记 (1=已删除, 0=正常)',
  PRIMARY KEY (`id`),
  KEY `idx_product_parent` (`product_id`, `parent_id`) COMMENT '产品父节点复合索引',
  KEY `idx_tree_path` (`tree_path`) COMMENT '树形路径索引',
  KEY `idx_updated_at` (`updated_at`) COMMENT '更新时间索引 (用于 New 标判定)',
  KEY `idx_solr_id` (`solr_id`) COMMENT 'Solr ID 索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产文件表';

-- ----------------------------
-- 4. 用户阅读状态表 (user_file_state)
-- ----------------------------
DROP TABLE IF EXISTS `user_file_state`;
CREATE TABLE `user_file_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `file_id` BIGINT NOT NULL COMMENT '文件 ID',
  `last_read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后一次阅读/下载时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `file_id`) COMMENT '用户文件唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户阅读状态表';

-- ----------------------------
-- 5. 用户收藏置顶表 (user_file_star)
-- ----------------------------
DROP TABLE IF EXISTS `user_file_star`;
CREATE TABLE `user_file_star` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `file_id` BIGINT NOT NULL COMMENT '文件 ID',
  `is_pinned` TINYINT NOT NULL DEFAULT 0 COMMENT '是否置顶 (1=是, 0=否)',
  `pin_order` INT NOT NULL DEFAULT 0 COMMENT '置顶排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `file_id`) COMMENT '用户文件唯一约束',
  KEY `idx_user_pin` (`user_id`, `is_pinned`, `pin_order`) COMMENT '用户置顶排序索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏置顶表';

-- ----------------------------
-- 6. 资产访问日志表 (asset_access_log)
-- ----------------------------
DROP TABLE IF EXISTS `asset_access_log`;
CREATE TABLE `asset_access_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `file_id` BIGINT NOT NULL COMMENT '文件 ID',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `product_id` BIGINT NOT NULL COMMENT '产品 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_recent` (`user_id`, `created_at`) COMMENT '用户最近访问索引',
  KEY `idx_product_hot` (`product_id`, `created_at`) COMMENT '产品热门统计索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产访问日志表';

-- ----------------------------
-- 7. 核心资产精选表 (asset_curated)
-- ----------------------------
DROP TABLE IF EXISTS `asset_curated`;
CREATE TABLE `asset_curated` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `product_id` BIGINT NOT NULL COMMENT '产品 ID',
  `file_id` BIGINT NOT NULL COMMENT '文件 ID',
  `display_order` INT NOT NULL DEFAULT 0 COMMENT '显示顺序',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_file` (`product_id`, `file_id`) COMMENT '产品文件唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核心资产精选表';

-- ----------------------------
-- 8. 热门搜索词表 (asset_hot_search)
-- ----------------------------
DROP TABLE IF EXISTS `asset_hot_search`;
CREATE TABLE `asset_hot_search` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `keyword` VARCHAR(100) NOT NULL COMMENT '搜索关键词',
  `search_count` INT NOT NULL DEFAULT 0 COMMENT '搜索次数统计',
  `is_active` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 (1=是, 0=否)',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword` (`keyword`) COMMENT '关键词唯一索引',
  KEY `idx_count` (`search_count`) COMMENT '搜索次数索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='热门搜索词表';

-- ----------------------------
-- 9. 编辑锁表 (edit_lock)
-- ----------------------------
DROP TABLE IF EXISTS `edit_lock`;
CREATE TABLE `edit_lock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
  `asset_file_id` BIGINT NOT NULL COMMENT '被锁定的文件 ID',
  `lock_ticket` VARCHAR(100) NOT NULL COMMENT '锁定令牌',
  `locked_by` BIGINT NOT NULL COMMENT '锁定人 ID',
  `locked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '锁定时间',
  `expires_at` DATETIME NOT NULL COMMENT '过期时间',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_id` (`asset_file_id`) COMMENT '文件锁定唯一约束'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='编辑锁表';

-- ----------------------------
-- 10. 用户关注产品表 (user_favorite_product)
-- ----------------------------
DROP TABLE IF EXISTS `user_favorite_product`;
CREATE TABLE `user_favorite_product` (
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `product_id` BIGINT NOT NULL COMMENT '产品 ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`user_id`, `product_id`) COMMENT '用户产品联合主键'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注产品表';

SET FOREIGN_KEY_CHECKS = 1;
