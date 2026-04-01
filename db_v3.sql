-- 1. 在 asset_product 表中新增 owner_ids 字段，用于存储逗号分隔的 ID 字符串
ALTER TABLE `asset_product` ADD COLUMN `owner_ids` VARCHAR(255) DEFAULT NULL COMMENT '产品负责人ID列表(逗号分隔)' AFTER `domain_name`;

-- 2. 将原有的 owner_id 数据迁移到新字段 owner_ids 中
-- 注意：这里将 bigint 转换为字符串存储
UPDATE `asset_product` SET `owner_ids` = CAST(`owner_id` AS CHAR) WHERE `owner_id` IS NOT NULL;

-- 3. (可选) 确认数据迁移无误后，删除旧的 owner_id 字段
-- 如果你担心风险，可以先注释掉下面这一行，等程序跑通后再执行
ALTER TABLE `asset_product` DROP COLUMN `owner_id`;

-- 4. (可选) 如果你希望保持字段名不变，也可以将新字段重命名回 owner_id (但类型已变为 varchar)
-- ALTER TABLE `asset_product` CHANGE COLUMN `owner_ids` `owner_id` VARCHAR(255) DEFAULT NULL COMMENT '产品负责人ID列表(逗号分隔)';
