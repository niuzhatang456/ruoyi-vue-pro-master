-- =====================================================================
-- 纪检业务数据清空脚本
-- 仅限本地开发库使用，请勿在生产环境执行！
-- 执行前请确认当前连接的是本地开发数据库。
-- 执行后，所有纪检业务正式表和导入暂存表的数据将被清空。
-- 系统表（sys_user / system_* / infra_* / tenant 等）不受影响。
-- =====================================================================
-- 生成日期：2026-06-05
-- =====================================================================

-- 执行前建议先查看各表记录数确认范围：
-- SELECT 'jijian_property' AS tbl, COUNT(*) AS cnt FROM jijian_property
-- UNION ALL SELECT 'jijian_lessee', COUNT(*) FROM jijian_lessee
-- UNION ALL SELECT 'jijian_lease_contract', COUNT(*) FROM jijian_lease_contract
-- UNION ALL SELECT 'jijian_attendance_daily', COUNT(*) FROM jijian_attendance_daily
-- UNION ALL SELECT 'jijian_leave_health', COUNT(*) FROM jijian_leave_health
-- UNION ALL SELECT 'jijian_leave_personal', COUNT(*) FROM jijian_leave_personal
-- UNION ALL SELECT 'jijian_business_trip', COUNT(*) FROM jijian_business_trip
-- UNION ALL SELECT 'jijian_compensatory_leave', COUNT(*) FROM jijian_compensatory_leave
-- UNION ALL SELECT 'jijian_canteen_supplier', COUNT(*) FROM jijian_canteen_supplier
-- UNION ALL SELECT 'jijian_import_record', COUNT(*) FROM jijian_import_record
-- UNION ALL SELECT 'jijian_import_parsed_data', COUNT(*) FROM jijian_import_parsed_data;

-- =====================================================================
-- Step 1: 清空 9 张正式业务表（P1 confirmWrite 写入目标）
-- 按外键依赖顺序（合同 → 人员/房产，然后其他）
-- =====================================================================

DELETE FROM jijian_lease_contract;

DELETE FROM jijian_lessee;

DELETE FROM jijian_property;

DELETE FROM jijian_attendance_daily;

DELETE FROM jijian_leave_health;

DELETE FROM jijian_leave_personal;

DELETE FROM jijian_business_trip;

DELETE FROM jijian_compensatory_leave;

DELETE FROM jijian_canteen_supplier;

-- =====================================================================
-- Step 2: 清空导入暂存表（OCR / Excel 解析结果表）
-- 清空后，录入历史将不可恢复，但不影响系统运行
-- =====================================================================

DELETE FROM jijian_import_parsed_data;

DELETE FROM jijian_import_record;

-- =====================================================================
-- 验证（执行后所有表应返回 0）：
-- SELECT 'jijian_property' AS tbl, COUNT(*) AS cnt FROM jijian_property
-- UNION ALL SELECT 'jijian_attendance_daily', COUNT(*) FROM jijian_attendance_daily
-- UNION ALL SELECT 'jijian_import_record', COUNT(*) FROM jijian_import_record;
-- =====================================================================
