-- ----------------------------
-- Database Incremental Migration Script V3.1
-- Target: 引入 14 天时效性窗口的 New 状态感知引擎
-- ----------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 1. 用户文件阅读状态表 (优化版)
-- 重点：增加了 last_read_at 的索引，方便定时任务快速清理过期数据
CREATE TABLE `user_file_state` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `file_id` BIGINT NOT NULL COMMENT '资产文件ID',
  `last_read_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后阅读时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `file_id`),
  KEY `idx_read_window` (`last_read_at`) -- 用于 O(1) 级别的定期清理
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户阅读状态表';

-- 2. 资产访问日志表 (优化版)
-- 重点：增加 (file_id, access_at) 复合索引，优化单产品榜单查询性能
CREATE TABLE `asset_access_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL COMMENT '访问用户ID',
  `file_id` BIGINT NOT NULL COMMENT '资产文件ID',
  `product_id` BIGINT NOT NULL COMMENT '所属产品ID',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '访问时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_recent` (`user_id`, `created_at`), 
  KEY `idx_prod_time_hot` (`product_id`, `created_at`, `file_id`), -- 支撑产品内 Top 统计
  KEY `idx_global_time_hot` (`created_at`, `file_id`) -- 支撑全行 Top 统计
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='资产访问流水日志表';

-- 3. 资产收藏及置顶表 (保持不变)
CREATE TABLE `user_file_star` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `file_id` BIGINT NOT NULL,
  `is_pinned` TINYINT(1) NOT NULL DEFAULT 0,
  `pin_order` INT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_file` (`user_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户资产收藏表';

-- 4. 业务推荐/核心资产表 (核心：支撑 [7.产品核心资产])
CREATE TABLE `asset_curated` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT NOT NULL COMMENT '产品ID',
  `file_id` BIGINT NOT NULL COMMENT '资产ID',
  `reason` VARCHAR(255) DEFAULT '' COMMENT '推荐理由',
  `display_order` INT NOT NULL DEFAULT 0 COMMENT '排序权重',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_file` (`product_id`, `file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='产品核心资产推荐表(管理员指定)';

-- 5. 全局搜索热词表 (核心：支撑 [3.热门搜索词])
CREATE TABLE `asset_hot_search` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `keyword` VARCHAR(100) NOT NULL COMMENT '搜索词',
  `search_count` INT NOT NULL DEFAULT 0 COMMENT '搜索频次',
  `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否展示',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_keyword` (`keyword`),
  KEY `idx_heat` (`search_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='全局搜索热词统计表';

-- 6. 对原 asset_file 表进行微调索引 (优化全行 New 榜查询)
ALTER TABLE `asset_file` ADD INDEX `idx_updated_at` (`updated_at`, `is_latest`, `is_deleted`);

SET FOREIGN_KEY_CHECKS = 1;
