-- ============================================================
-- 考勤日报表新增 week_day 字段（星期）
-- 执行日期：2026-06-10
-- 执行前确认：SHOW COLUMNS FROM jijian_attendance_daily LIKE 'week_day';
-- 若已存在请跳过
-- ============================================================

ALTER TABLE `jijian_attendance_daily`
  ADD COLUMN `week_day` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
    NULL DEFAULT NULL
    COMMENT '星期（如：星期一、星期五，原样存储 Excel 原文）'
    AFTER `attendance_date`;

-- 验证：
-- SHOW COLUMNS FROM jijian_attendance_daily;
