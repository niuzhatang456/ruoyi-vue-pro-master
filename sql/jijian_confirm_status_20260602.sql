-- ============================================================
-- 纪检录入模块：为 jijian_import_parsed_data 添加独立 confirm_status 字段
-- 文件：sql/jijian_confirm_status_20260602.sql
-- 目的：将解析状态（status）和确认状态（confirm_status）彻底分离
-- 执行方式：手工在 MySQL 连接到 ruoyi_vue_pro 库后执行
-- ============================================================

-- ── Step 1：添加 confirm_status 列（pending / confirmed）──────────────────
ALTER TABLE jijian_import_parsed_data
    ADD COLUMN confirm_status VARCHAR(20) NULL
        COMMENT '确认状态: pending(待确认) | confirmed(已确认写入正式表)'
        AFTER status;

-- ── Step 2：存量数据迁移 ───────────────────────────────────────────────────
-- 将老代码遗留的 status='confirmed' 记录：
--   confirm_status 置为 confirmed（表示已写入正式表）
-- 注意：不改 status 字段的值，保留兼容性（前端双重检查）
UPDATE jijian_import_parsed_data
    SET confirm_status = 'confirmed'
    WHERE status = 'confirmed'
      AND confirm_status IS NULL;

-- 将 status='success' 且尚未确认的记录 confirm_status 初始化为 pending
UPDATE jijian_import_parsed_data
    SET confirm_status = 'pending'
    WHERE status = 'success'
      AND confirm_status IS NULL;

-- ── Step 3：验证（执行后检查）────────────────────────────────────────────────
-- SHOW COLUMNS FROM jijian_import_parsed_data LIKE 'confirm_status';
-- SELECT status, confirm_status, COUNT(*) FROM jijian_import_parsed_data GROUP BY 1, 2;
