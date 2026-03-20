---
Document_ID: 05
Title: Database Schema Design
Chinese_Name: 数据库结构设计
Version: V1.1
Status: Approved
AI_Model: Gemini 3 Flash
Last_Modified: 2026-03-20
---

# 05 数据库结构设计 (Database Schema Design)

> **核心目标**：支持资产文件的快速定位、阅读状态感知、个性化收藏及产品维度管理逻辑。

## 1. 数据库概览
*   **数据库类型**：MySQL 8.0
*   **字符集**：utf8mb4
*   **存储引擎**：InnoDB
*   **命名规范**：下划线命名法（Snake Case）

## 2. 核心表结构

### 2.1 资产文件表 (`asset_file`)
存储文件和文件夹的元数据。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `product_id` | bigint | INDEX | 所属产品 ID (0 表示公共专区) |
| `parent_id` | bigint | INDEX | 父节点 ID (0 表示根节点) |
| `tree_path` | varchar(500) | INDEX | 树形路径 (如 /0/1/5/) |
| `node_type` | tinyint | - | 1=文件夹, 2=文件 |
| `file_name` | varchar(255) | - | 文件或文件夹名称 |
| `ext` | varchar(20) | - | 文件扩展名 |
| `file_size` | bigint | - | 文件大小 (Bytes) |
| `version_no` | int | - | 版本号 |
| `is_latest` | tinyint | - | 是否为最新版本 (1=是, 0=否) |
| `local_path` | varchar(500) | - | 物理存储路径 |
| `pdf_path` | varchar(500) | - | 预览用 PDF 路径 |
| `solr_id` | varchar(100) | - | 对应的 Solr 文档 ID |
| `parse_status` | tinyint | - | 0=无需, 1=排队, 2=解析中, 3=成功, 4=失败 |
| `created_by` | bigint | - | 创建者 ID |
| `created_at` | datetime | - | 创建时间 |
| `updated_at` | datetime | INDEX | 更新时间 |
| `is_deleted` | tinyint | - | 逻辑删除标记 |

### 2.2 产品信息表 (`asset_product`)
存储业务条线/产品维度的基础信息。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `product_name` | varchar(100) | - | 产品名称 |
| `product_code` | varchar(50) | UNIQUE | 产品编码 |
| `team_name` | varchar(100) | - | 所属团队 |
| `domain_name` | varchar(100) | - | 所属业务域 |
| `owner_id` | bigint | - | 负责人 ID |
| `asset_count` | int | - | 资产总数统计 |
| `created_at` | datetime | - | 创建时间 |
| `updated_at` | datetime | - | 更新时间 |
| `is_deleted` | tinyint | - | 逻辑删除标记 |

### 2.3 用户信息表 (`asset_user`)
存储系统用户信息及角色。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `username` | varchar(50) | UNIQUE | 登录用户名 |
| `password_hash` | varchar(255) | - | 密码哈希 |
| `real_name` | varchar(50) | - | 真实姓名 |
| `emp_no` | varchar(20) | - | 员工工号 |
| `role_type` | tinyint | - | 1=管理员, 2=测试经理, 3=普通用户, 4=外购人员 |
| `created_at` | datetime | - | 创建时间 |
| `updated_at` | datetime | - | 更新时间 |
| `is_deleted` | tinyint | - | 逻辑删除标记 |

### 2.4 用户阅读状态表 (`user_file_state`)
支撑 "New" 标消除机制。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `user_id` | bigint | UNIQUE(user, file) | 用户 ID |
| `file_id` | bigint | UNIQUE(user, file) | 文件 ID |
| `last_read_at` | datetime | - | 用户最后一次阅读/下载时间 |

### 2.5 用户收藏置顶表 (`user_file_star`)
支撑“我的收藏”及“置顶”功能。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `user_id` | bigint | INDEX | 用户 ID |
| `file_id` | bigint | - | 文件 ID |
| `is_pinned` | tinyint | - | 是否置顶 (1=是, 0=否) |
| `pin_order` | int | - | 置顶排序权重 |
| `created_at` | datetime | - | 收藏时间 |

### 2.6 资产访问日志表 (`asset_access_log`)
支撑“最近访问”和“使用排行”。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `file_id` | bigint | INDEX | 文件 ID |
| `user_id` | bigint | INDEX | 用户 ID |
| `product_id` | bigint | - | 产品 ID |
| `created_at` | datetime | INDEX | 访问时间 |

### 2.7 热门搜索词表 (`asset_hot_search`)
支撑搜索框下的热门词推荐。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `keyword` | varchar(100) | UNIQUE | 搜索关键词 |
| `search_count` | int | - | 搜索次数统计 |
| `is_active` | tinyint | - | 是否启用 |
| `updated_at` | datetime | - | 最后更新时间 |

### 2.8 编辑锁表 (`edit_lock`)
支撑多人协作时的文件编辑冲突控制。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `id` | bigint | PRI, AUTO_INC | 主键 ID |
| `asset_file_id` | bigint | UNIQUE | 被锁定的文件 ID |
| `lock_ticket` | varchar(100) | - | 锁定令牌 |
| `locked_by` | bigint | - | 锁定人 ID |
| `locked_at` | datetime | - | 锁定时间 |
| `expires_at` | datetime | - | 过期时间 |

### 2.9 用户关注产品表 (`user_favorite_product`)
支撑首页“业务版图”中的产品关注功能。

| 字段名 | 类型 | 约束 | 备注 |
| :--- | :--- | :--- | :--- |
| `user_id` | bigint | PRI | 用户 ID |
| `product_id` | bigint | PRI | 产品 ID |
| `created_at` | datetime | - | 关注时间 |

## 3. 索引优化策略
1.  **树形查询优化**：在 `tree_path` 上建立前缀索引。
2.  **New 标判定优化**：在 `asset_file.updated_at` 上建立索引。
3.  **去重统计优化**：在 `asset_access_log` 的 `(user_id, created_at)` 上建立复合索引。
4.  **唯一性约束**：在 `user_file_state` 和 `edit_lock` 上使用唯一索引防止并发冲突。

## 4. 关联关系图 (ERD 逻辑)
*   `asset_user` (1) <---> (N) `user_file_star` / `asset_access_log` / `user_file_state`
*   `asset_product` (1) <---> (N) `asset_file` / `asset_curated`
*   `asset_file` (1) <---> (1) `edit_lock`
*   `asset_user` (N) <---> (N) `asset_product` (通过 `user_favorite_product`)
