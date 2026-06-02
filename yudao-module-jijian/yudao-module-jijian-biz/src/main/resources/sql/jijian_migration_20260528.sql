-- ============================================================
-- 纪检模块迁移 SQL - 20260528
-- 执行顺序：按序执行，每条语句单独确认后执行
-- 数据库：ruoyi_vue_pro（MySQL 8.0）
-- ============================================================

-- ----------------------------------------------------------------
-- 步骤 0：前置建表（若尚未执行，先执行这两条）
-- 注意：生产环境请先确认表是否已存在，已存在则跳过
-- ----------------------------------------------------------------

-- 0-A: jijian_import_record
CREATE TABLE IF NOT EXISTS `jijian_import_record` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `file_name`          varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传文件名',
  `source_type`        varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型：ocr / excel / drag',
  `detected_form_type` varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '自动识别表单类型',
  `status`             varchar(32)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：processing / success / failed',
  `created_at`         datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP             COMMENT '业务创建时间（冗余，与 create_time 同值）',
  `creator`            varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP             COMMENT '创建时间',
  `updater`            varchar(64)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            bit(1)       NOT NULL DEFAULT b'0'                           COMMENT '是否删除',
  `tenant_id`          bigint       NOT NULL DEFAULT 0                              COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检导入记录表';

-- 0-B: jijian_import_parsed_data
CREATE TABLE IF NOT EXISTS `jijian_import_parsed_data` (
  `id`                   bigint        NOT NULL AUTO_INCREMENT COMMENT '主键编号',
  `import_record_id`     bigint        NOT NULL                                     COMMENT '关联导入记录ID',
  `form_type`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '识别表单类型',
  `raw_text`             longtext      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT '原始文本',
  `parsed_json`          json                                                        COMMENT '结构化解析 JSON',
  `confidence`           decimal(5, 4) NULL DEFAULT NULL                             COMMENT '解析置信度',
  `status`               varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态：success / failed / confirmed',
  `error_msg`            varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '失败原因',
  `confirmed_property_id` bigint       NULL DEFAULT NULL                             COMMENT '确认写入后的正式房产记录ID',
  `confirm_time`         datetime      NULL DEFAULT NULL                             COMMENT '确认写入时间',
  `creator`              varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP           COMMENT '创建时间',
  `updater`              varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time`          datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`              bit(1)        NOT NULL DEFAULT b'0'                         COMMENT '是否删除',
  `tenant_id`            bigint        NOT NULL DEFAULT 0                            COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_import_record_id` (`import_record_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检导入解析数据表';

-- ----------------------------------------------------------------
-- 步骤 1：jijian_property 补充多租户字段
-- 背景：PropertyDO 已从 BaseDO 切换为 TenantBaseDO，
--       框架租户插件会对该表追加 AND tenant_id = ? 过滤，
--       若表中无此列则运行时报 SQL 错误。
-- ----------------------------------------------------------------
ALTER TABLE `jijian_property`
  ADD COLUMN `tenant_id` bigint NOT NULL DEFAULT 0
    COMMENT '租户编号' AFTER `deleted`;

-- ----------------------------------------------------------------
-- 步骤 2：jijian_property 追加来源追溯字段及唯一索引
-- 唯一索引确保同一条解析记录不会重复写入房产，实现幂等。
-- ----------------------------------------------------------------
ALTER TABLE `jijian_property`
  ADD COLUMN `source_parsed_data_id` bigint NULL DEFAULT NULL
    COMMENT '来源解析数据ID，关联 jijian_import_parsed_data.id' AFTER `remark`;

ALTER TABLE `jijian_property`
  ADD UNIQUE KEY `uk_source_parsed_data_id` (`source_parsed_data_id`);

-- ----------------------------------------------------------------
-- 步骤 3：jijian_import_parsed_data 若已存在（之前已手动建表），
--         追加确认相关字段
-- 注意：若步骤 0-B 是新建，则这两列已包含在内，跳过此步骤
-- ----------------------------------------------------------------
ALTER TABLE `jijian_import_parsed_data`
  ADD COLUMN `confirmed_property_id` bigint NULL DEFAULT NULL
    COMMENT '确认写入后的正式房产记录ID' AFTER `error_msg`;

ALTER TABLE `jijian_import_parsed_data`
  ADD COLUMN `confirm_time` datetime NULL DEFAULT NULL
    COMMENT '确认写入时间' AFTER `confirmed_property_id`;

-- ----------------------------------------------------------------
-- 执行完成后，请确认以下内容：
--   SELECT TABLE_NAME, COLUMN_NAME FROM information_schema.COLUMNS
--   WHERE TABLE_SCHEMA = 'ruoyi_vue_pro'
--     AND TABLE_NAME IN ('jijian_property','jijian_import_parsed_data')
--     AND COLUMN_NAME IN ('tenant_id','source_parsed_data_id',
--                         'confirmed_property_id','confirm_time');
-- ----------------------------------------------------------------
