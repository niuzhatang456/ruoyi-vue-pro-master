-- 纪检信息系统功能整合补丁：仅操作 jijian_ 业务表。
-- 1) 出差表补齐“出差人数”字段。
-- 2) 确保民生商品市场零售价格公告表存在，供拖拽/Excel 识别和智能查询只读访问。

DELIMITER $$
DROP PROCEDURE IF EXISTS `jijian_add_column_if_missing`$$
CREATE PROCEDURE `jijian_add_column_if_missing`(
  IN table_name_value varchar(64),
  IN column_name_value varchar(64),
  IN column_definition_value varchar(1000)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = table_name_value
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `', table_name_value, '` ADD COLUMN `',
                      column_name_value, '` ', column_definition_value);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL jijian_add_column_if_missing(
  'jijian_business_trip',
  'trip_people_count',
  'int NULL COMMENT ''出差人数'' AFTER `trip_personnel`'
);

DROP PROCEDURE IF EXISTS `jijian_add_column_if_missing`;

CREATE TABLE IF NOT EXISTS `jijian_canteen_market_price` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `item_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '项目名称/商品名称',
  `spec_level` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格/等级',
  `unit` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `price` decimal(10,2) NULL DEFAULT NULL COMMENT '价格',
  `price_point` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采价点',
  `price_month` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '公告年月，如 2025-02',
  `source_title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '公告标题',
  `source_file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '来源文件名',
  `source_parsed_data_id` bigint NULL DEFAULT NULL COMMENT '来源解析数据ID',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_jijian_canteen_market_price_item_month` (`item_name`, `price_month`),
  KEY `idx_jijian_canteen_market_price_source` (`source_parsed_data_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='纪检-民生商品市场零售价格公告表';
