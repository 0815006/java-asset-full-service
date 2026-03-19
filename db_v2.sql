-- ----------------------------
-- Database Migration Script V2.0
-- Based on PRD V2.1
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- Drop new tables if they exist (re-run safety)
DROP TABLE IF EXISTS `asset_user`;
DROP TABLE IF EXISTS `asset_product`;
DROP TABLE IF EXISTS `user_favorite_product`;
DROP TABLE IF EXISTS `asset_file`;
DROP TABLE IF EXISTS `edit_lock`;

-- ----------------------------
-- 1. Table structure for asset_user (极简用户表)
-- ----------------------------
CREATE TABLE `asset_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键自增',
  `username` VARCHAR(50) NOT NULL COMMENT '登录账号',
  `password_hash` VARCHAR(255) NOT NULL COMMENT '密码Hash',
  `real_name` VARCHAR(50) NOT NULL COMMENT '真实姓名 (用于前端防泄露水印)',
  `emp_no` VARCHAR(50) NOT NULL COMMENT '工号 (用于前端防泄露水印)',
  `role_type` TINYINT NOT NULL DEFAULT 3 COMMENT '全局角色：1=管理员, 2=测试经理, 3=普通用户',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_emp_no` (`emp_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='极简用户身份表';

-- ----------------------------
-- 2. Table structure for asset_product (产品域物理隔离表)
-- ----------------------------
CREATE TABLE `asset_product` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_name` VARCHAR(100) NOT NULL COMMENT '产品名称 (如: 核心业务系统)',
  `team_name` VARCHAR(100) NOT NULL COMMENT '归属团队',
  `domain_name` VARCHAR(100) NOT NULL COMMENT '归属领域 (支撑产品卡片按领域分组)',
  `owner_id` BIGINT NOT NULL COMMENT '负责人ID (关联 asset_user.id)',
  `asset_count` INT NOT NULL DEFAULT 0 COMMENT '资产统计冗余字段 (供前端极速展示)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_owner_id` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品域隔离表';

-- ----------------------------
-- 3. Table structure for user_favorite_product (用户产品收藏关联表 - V2.1新增)
-- ----------------------------
CREATE TABLE `user_favorite_product` (
  `user_id` BIGINT NOT NULL,
  `product_id` BIGINT NOT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户收藏产品表';

-- ----------------------------
-- 4. Table structure for asset_file (核心资产树表 - 💡含防爆物化路径)
-- ----------------------------
CREATE TABLE `asset_file` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '归属产品ID (0=公共/管理专区)',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点ID (0=产品根目录)',
  `tree_path` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '物化路径(如 /0/1001/1002/，极速定位防爆)',
  `node_type` TINYINT NOT NULL COMMENT '1=文件夹, 2=文件',
  `file_name` VARCHAR(255) NOT NULL COMMENT '文件夹名或完整文件名(含后缀)',
  `ext` VARCHAR(20) NOT NULL DEFAULT '' COMMENT '文件扩展名 (如 md, docx, pdf)',
  `file_size` BIGINT NOT NULL DEFAULT 0 COMMENT '文件大小 (Bytes)',
  `version_no` INT NOT NULL DEFAULT 1 COMMENT '版本号 (如 1=V1.0)',
  `is_latest` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否最新版 (1=最新, 0=历史)',
  `local_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '本地存储绝对/相对路径',
  `pdf_path` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '降级转码的PDF副本路径',
  `solr_id` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '全文检索 Solr Document ID',
  `parse_status` TINYINT NOT NULL DEFAULT 0 COMMENT '异步解析: 0=无需, 1=排队, 2=解析, 3=成功, 4=失败',
  `created_by` BIGINT NOT NULL COMMENT '上传人/创建人ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '全局逻辑软删除',
  PRIMARY KEY (`id`),
  KEY `idx_tree_search` (`product_id`, `parent_id`, `is_latest`, `is_deleted`),
  KEY `idx_tree_path` (`tree_path`),
  KEY `idx_solr_id` (`solr_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='核心资产无限级树表';

-- ----------------------------
-- 5. Table structure for edit_lock (并发协同编辑锁表)
-- ----------------------------
CREATE TABLE `edit_lock` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `asset_file_id` BIGINT NOT NULL COMMENT '被锁定的文件ID',
  `lock_ticket` VARCHAR(64) NOT NULL COMMENT '防幽灵覆盖的凭证(UUID)',
  `locked_by` BIGINT NOT NULL COMMENT '持锁用户ID',
  `locked_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires_at` DATETIME NOT NULL COMMENT '锁过期时间(心跳刷新)',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '锁释放标记(1=主动释放或系统回收)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_file_id` (`asset_file_id`),
  UNIQUE KEY `uk_lock_ticket` (`lock_ticket`),
  KEY `idx_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Markdown并发协同编辑独占锁表';

SET FOREIGN_KEY_CHECKS = 1;