-- ==============================================================
-- 食堂供应表扩展字段迁移脚本
-- 迁移日期: 2026-06-10
-- 目标表:   jijian_canteen_supplier
-- 说明:     新增 quantity, subtotal, supplier_name, supply_date 四列
--            **本脚本已在当前开发数据库执行，新环境部署时需执行此脚本**
-- ==============================================================
--
-- 执行前检查（可手动运行确认当前字段情况）：
--   SHOW COLUMNS FROM jijian_canteen_supplier;
--
-- 注意事项：
--   1. 若字段已存在，直接执行 ALTER TABLE 会报错，请先确认字段不存在再执行。
--   2. 不清表、不删表、不重建表，只追加列。
--   3. 对已有数据无影响（新列均允许 NULL）。
-- ==============================================================

-- 以下语句请在字段不存在时执行
-- （新环境从零部署时直接执行；已运行过的环境跳过或先检查）

ALTER TABLE `jijian_canteen_supplier`
    ADD COLUMN `quantity`      DECIMAL(10,2) NULL    COMMENT '数量'     AFTER `unit`,
    ADD COLUMN `subtotal`      DECIMAL(10,2) NULL    COMMENT '小计金额'  AFTER `price`,
    ADD COLUMN `supplier_name` VARCHAR(256)  NULL    COMMENT '供应商名称' AFTER `subtotal`,
    ADD COLUMN `supply_date`   DATE          NULL    COMMENT '配送日期'  AFTER `supplier_name`;

-- ==============================================================
-- 执行后验证：
-- ==============================================================
-- SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_COMMENT
-- FROM information_schema.COLUMNS
-- WHERE TABLE_SCHEMA = DATABASE()
--   AND TABLE_NAME = 'jijian_canteen_supplier'
-- ORDER BY ORDINAL_POSITION;
--
-- 期望结果中包含：
--   quantity      | decimal(10,2) | YES | 数量
--   subtotal      | decimal(10,2) | YES | 小计金额
--   supplier_name | varchar(256)  | YES | 供应商名称
--   supply_date   | date          | YES | 配送日期
-- ==============================================================
