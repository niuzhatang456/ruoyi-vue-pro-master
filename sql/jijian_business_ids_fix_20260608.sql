-- 修复 jijian_import_parsed_data.business_ids 字段过短问题
-- 原因：大量行（如 16244 行）确认写入时，business_ids JSON 数组超出 VARCHAR(512) 限制
-- 修复：改为 TEXT 类型（最大 65535 字节）
-- 适用版本：本轮 2026-06-08 fix

ALTER TABLE jijian_import_parsed_data
    MODIFY COLUMN business_ids TEXT;
