-- 出差表结构漂移修复：仅新增和回填字段，不删除旧字段。
DELIMITER $$
DROP PROCEDURE IF EXISTS `jijian_add_business_trip_column`$$
CREATE PROCEDURE `jijian_add_business_trip_column`(
  IN column_name_value varchar(64),
  IN column_definition_value varchar(1000)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jijian_business_trip'
      AND COLUMN_NAME = column_name_value
  ) THEN
    SET @ddl = CONCAT('ALTER TABLE `jijian_business_trip` ADD COLUMN `',
                      column_name_value, '` ', column_definition_value);
    PREPARE stmt FROM @ddl;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;

CALL jijian_add_business_trip_column('trip_reason', 'varchar(256) NULL COMMENT ''出差事由'' AFTER `employee_no`');
CALL jijian_add_business_trip_column('departure_place', 'varchar(128) NULL COMMENT ''出发地'' AFTER `trip_reason`');
CALL jijian_add_business_trip_column('destination', 'varchar(128) NULL COMMENT ''目的地'' AFTER `departure_place`');
CALL jijian_add_business_trip_column('start_date', 'datetime NULL COMMENT ''出差开始日期'' AFTER `destination`');
CALL jijian_add_business_trip_column('end_date', 'datetime NULL COMMENT ''出差结束日期'' AFTER `start_date`');
CALL jijian_add_business_trip_column('trip_days', 'decimal(8,2) NULL COMMENT ''出差天数'' AFTER `end_date`');
CALL jijian_add_business_trip_column('trip_personnel', 'varchar(500) NULL COMMENT ''出差人员'' AFTER `trip_days`');
DROP PROCEDURE IF EXISTS `jijian_add_business_trip_column`;

-- 兼容旧环境已有数据。旧列保留为 deprecated，应用稳定后再单独评估删除。
UPDATE `jijian_business_trip`
SET `trip_reason` = COALESCE(`trip_reason`, `leave_reason`),
    `start_date` = COALESCE(`start_date`, `start_time`),
    `end_date` = COALESCE(`end_date`, `end_time`),
    `trip_days` = COALESCE(`trip_days`, `leave_days`)
WHERE `deleted` = b'0'
  AND (`trip_reason` IS NULL OR `start_date` IS NULL OR `end_date` IS NULL OR `trip_days` IS NULL);
