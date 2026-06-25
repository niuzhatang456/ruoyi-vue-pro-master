-- 租赁合同表新增"合同时间"（签订/记载时间）字段，区别于合同开始/结束时间。
-- 已有环境执行本脚本；新环境从零初始化时 jijian_input_module_completion_20260528.sql 已含该列。
SET @has_contract_time := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'jijian_lease_contract'
    AND COLUMN_NAME = 'contract_time'
);
SET @add_contract_time_sql := IF(
  @has_contract_time = 0,
  'ALTER TABLE `jijian_lease_contract` ADD COLUMN `contract_time` datetime NULL COMMENT ''合同时间（签订/记载时间）'' AFTER `lessee_id`',
  'SELECT 1'
);
PREPARE add_contract_time_stmt FROM @add_contract_time_sql;
EXECUTE add_contract_time_stmt;
DEALLOCATE PREPARE add_contract_time_stmt;

-- 验证：SHOW COLUMNS FROM jijian_lease_contract LIKE 'contract_time';
