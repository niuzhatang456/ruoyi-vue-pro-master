-- ============================================================
-- 纪检录入模块补丁：确认写入追溯字段 + 业务表名回写
-- 文件：sql/jijian_import_module_fix_20260601.sql
-- 执行前提：已执行 jijian_input_module_completion_20260528.sql
-- 执行方式：手工在 MySQL 连接到 ruoyi-vue-pro 库后逐段执行
-- ============================================================

-- ── 1. jijian_import_parsed_data 补充追溯字段 ──────────────────────────────

-- 1-a. 确认人 ID
-- 注意：MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，执行前请先确认列是否存在
ALTER TABLE jijian_import_parsed_data
    ADD COLUMN confirm_user_id BIGINT NULL COMMENT '确认写入操作人ID' AFTER confirm_time;

ALTER TABLE jijian_import_parsed_data
    ADD COLUMN business_table VARCHAR(64) NULL COMMENT '写入的正式业务表名' AFTER confirm_user_id;

ALTER TABLE jijian_import_parsed_data
    ADD COLUMN business_ids VARCHAR(512) NULL COMMENT '写入的正式业务记录ID列表(JSON数组)' AFTER business_table;

-- ── 2. 验证字段已添加 ────────────────────────────────────────────────────────

-- 执行后请用以下 SQL 检查：
-- DESC jijian_import_parsed_data;
-- 应能看到 confirm_time / confirm_user_id / business_table / business_ids 四列

-- ── 3. 补充注释（可选）──────────────────────────────────────────────────────

-- 修改 status 字段注释，明确说明枚举值
ALTER TABLE jijian_import_parsed_data
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'success'
        COMMENT '解析/确认状态: success(解析成功,待确认) | failed(解析或确认失败) | confirmed(已确认写入)';

-- ── 4. 说明 ──────────────────────────────────────────────────────────────────
-- jijian_import_record 表当前字段已满足需求，本次不变：
--   id / file_name / source_type / detected_form_type / status / created_at / tenant_id
-- 其中 status=success 表示已成功解析并写入 parsed_data；status=failed 表示解析失败。
