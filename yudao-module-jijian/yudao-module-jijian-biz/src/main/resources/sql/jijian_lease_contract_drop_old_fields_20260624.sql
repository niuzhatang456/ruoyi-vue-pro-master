-- 第四阶段：租赁合同表旧字段清理。
-- 只删除已由新字段替代的旧列；历史建表/补字段 SQL 不回改。

SET @drop_cols := (
    SELECT GROUP_CONCAT(CONCAT('DROP COLUMN `', COLUMN_NAME, '`') ORDER BY FIELD(
        COLUMN_NAME,
        'property_id',
        'lessee_id',
        'party_a',
        'contract_time',
        'contract_start_time',
        'contract_end_time',
        'amount',
        'payment_status',
        'water_electricity_mgmt',
        'contract_summary'
    ) SEPARATOR ', ')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'jijian_lease_contract'
      AND COLUMN_NAME IN (
          'property_id',
          'lessee_id',
          'party_a',
          'contract_time',
          'contract_start_time',
          'contract_end_time',
          'amount',
          'payment_status',
          'water_electricity_mgmt',
          'contract_summary'
      )
);

SET @drop_sql := IF(
    @drop_cols IS NULL OR @drop_cols = '',
    'SELECT ''jijian_lease_contract old fields already clean'' AS message',
    CONCAT('ALTER TABLE `jijian_lease_contract` ', @drop_cols)
);

PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
