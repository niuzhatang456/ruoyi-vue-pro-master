-- ============================================================
-- 纪检系统录入模块完整建表 SQL
-- 生成日期：2026-05-28
-- 执行说明：请按照"执行步骤"注释逐条确认后手动执行
-- 数据库：ruoyi_vue_pro（MySQL 8.0, utf8mb4_unicode_ci）
-- ============================================================

-- ================================================================
-- 已存在、无需执行的表（确认无误后跳过）：
--   jijian_property       ← 已建表，含 tenant_id、source_parsed_data_id
--   jijian_import_record  ← 已建表
--   jijian_import_parsed_data ← 已建表，含 confirmed_property_id、confirm_time
-- ================================================================

-- ================================================================
-- 步骤 A：在 jijian_import_parsed_data 补充"用户校正 JSON"字段
--  【若已执行过请跳过；可用：SHOW COLUMNS FROM jijian_import_parsed_data 确认是否存在 corrected_json】
-- ================================================================

ALTER TABLE `jijian_import_parsed_data`
  ADD COLUMN `corrected_json` json NULL DEFAULT NULL
    COMMENT '用户在前端校正后保存的结构化数据（覆盖 parsed_json 参与确认写入）'
    AFTER `parsed_json`;

-- ================================================================
-- 步骤 B：新建 jijian_lessee（房屋租赁人员信息表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_lessee` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `entity_type`           varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '主体类型：个人 / 组织',
  `contact_name`          varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '联系人姓名',
  `phone`                 varchar(20)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `id_card`               varchar(20)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '身份证号',
  `business_license`      varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '营业执照号',
  `is_internal_staff`     bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否单位内部人员',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID，关联 jijian_import_parsed_data.id',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检房屋租赁人员信息表';

-- ================================================================
-- 步骤 C：新建 jijian_lease_contract（租赁合同表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_lease_contract` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `property_id`           bigint        NULL DEFAULT NULL                           COMMENT '关联房产ID（可选，手动关联）',
  `lessee_id`             bigint        NULL DEFAULT NULL                           COMMENT '关联租赁人员ID（可选，手动关联）',
  `contract_start_time`   datetime      NULL DEFAULT NULL                           COMMENT '合同开始时间',
  `contract_end_time`     datetime      NULL DEFAULT NULL                           COMMENT '合同结束时间',
  `amount`                decimal(12,2) NULL DEFAULT NULL                           COMMENT '合同金额（元）',
  `payment_status`        varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '支付情况',
  `water_electricity_mgmt` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '水电费管理方式',
  `contract_summary`      text          CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL             COMMENT '合同内容摘要',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检租赁合同表';

-- ================================================================
-- 步骤 D：新建 jijian_attendance_daily（考勤日报表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_attendance_daily` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `employee_name`         varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '员工姓名',
  `employee_no`           varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工编号',
  `department`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `checkin_time`          datetime      NULL DEFAULT NULL                           COMMENT '上班打卡时间',
  `checkin_result`        varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '上班打卡结果',
  `checkin_location`      varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '上班打卡地点',
  `checkin_remark`        varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '上班备注',
  `checkout_time`         datetime      NULL DEFAULT NULL                           COMMENT '下班打卡时间',
  `checkout_result`       varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下班打卡结果',
  `checkout_location`     varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下班打卡地点',
  `checkout_remark`       varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '下班备注',
  `attendance_date`       date          NULL DEFAULT NULL                           COMMENT '考勤日期',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID（多行共享同一来源，非唯一索引）',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE,
  KEY `idx_attendance_date` (`attendance_date`) USING BTREE,
  KEY `idx_employee_no` (`employee_no`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检考勤日报表';

-- ================================================================
-- 步骤 E：新建 jijian_leave_health（疗休养请假表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_leave_health` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `department`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `applicant_name`        varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '申请人姓名',
  `employee_no`           varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工编号',
  `leave_location`        varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '休假地点',
  `start_time`            datetime      NULL DEFAULT NULL                           COMMENT '疗养假开始时间',
  `end_time`              datetime      NULL DEFAULT NULL                           COMMENT '疗养假结束时间',
  `leave_days`            decimal(5,1)  NULL DEFAULT NULL                           COMMENT '请假天数',
  `work_years`            varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '工作年限',
  `start_work_time`       datetime      NULL DEFAULT NULL                           COMMENT '参加工作时间',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检疗休养请假表';

-- ================================================================
-- 步骤 F：新建 jijian_leave_personal（事假表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_leave_personal` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `department`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `applicant_name`        varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '申请人姓名',
  `employee_no`           varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工编号',
  `leave_type`            varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请假类型',
  `leave_reason`          varchar(256)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请假事由',
  `start_time`            datetime      NULL DEFAULT NULL                           COMMENT '请假开始时间',
  `end_time`              datetime      NULL DEFAULT NULL                           COMMENT '请假结束时间',
  `leave_days`            decimal(5,1)  NULL DEFAULT NULL                           COMMENT '请假天数',
  `is_outside`            bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否出义',
  `outside_location`      varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出义具体地点',
  `leave_status`          varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请假状态',
  `leave_month`           varchar(8)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请假月份（格式：2026-05）',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检事假表';

-- ================================================================
-- 步骤 G：新建 jijian_business_trip（出差表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_business_trip` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `department`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `applicant_name`        varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '申请人姓名',
  `employee_no`           varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工编号',
  `leave_type`            varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '请假类型',
  `leave_reason`          varchar(256)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出差事由',
  `start_time`            datetime      NULL DEFAULT NULL                           COMMENT '出差开始时间',
  `end_time`              datetime      NULL DEFAULT NULL                           COMMENT '出差结束时间',
  `leave_days`            decimal(5,1)  NULL DEFAULT NULL                           COMMENT '出差天数',
  `is_outside`            bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否出义',
  `outside_location`      varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出义具体地点',
  `leave_status`          varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '状态',
  `leave_month`           varchar(8)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出差月份（格式：2026-05）',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检出差表';

-- ================================================================
-- 步骤 H：新建 jijian_compensatory_leave（调休表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_compensatory_leave` (
  `id`                          bigint        NOT NULL AUTO_INCREMENT               COMMENT '主键编号',
  `applicant_name`              varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '申请人姓名',
  `employee_no`                 varchar(32)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '员工编号',
  `department`                  varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '部门',
  `overtime_start_time`         datetime      NULL DEFAULT NULL                     COMMENT '加班开始时间',
  `overtime_start_shift`        varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '加班开始班次',
  `overtime_end_time`           datetime      NULL DEFAULT NULL                     COMMENT '加班结束时间',
  `overtime_end_shift`          varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '加班结束班次',
  `compensatory_start_time`     datetime      NULL DEFAULT NULL                     COMMENT '调休开始时间',
  `compensatory_start_shift`    varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调休开始班次',
  `compensatory_end_time`       datetime      NULL DEFAULT NULL                     COMMENT '调休结束时间',
  `compensatory_end_shift`      varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调休结束班次',
  `compensatory_duration`       varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '调休时长',
  `is_outside`                  bit(1)        NOT NULL DEFAULT b'0'                 COMMENT '是否出义',
  `outside_location`            varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '出义具体地址',
  `remark`                      varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id`       bigint        NULL DEFAULT NULL                     COMMENT '来源解析数据ID',
  `creator`                     varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP    COMMENT '创建时间',
  `updater`                     varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time`                 datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`                     bit(1)        NOT NULL DEFAULT b'0'                 COMMENT '是否删除',
  `tenant_id`                   bigint        NOT NULL DEFAULT 0                    COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检调休表';

-- ================================================================
-- 步骤 I：新建 jijian_canteen_supplier（食堂供应商信息表）
-- ================================================================
CREATE TABLE IF NOT EXISTS `jijian_canteen_supplier` (
  `id`                    bigint        NOT NULL AUTO_INCREMENT                     COMMENT '主键编号',
  `item_name`             varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL          COMMENT '项目名称',
  `spec_level`            varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规格/等级',
  `unit`                  varchar(16)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '单位',
  `price`                 decimal(10,2) NULL DEFAULT NULL                           COMMENT '价格（元）',
  `purchase_point`        varchar(128)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '采价点',
  `remark`                varchar(500)  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '备注',
  `source_parsed_data_id` bigint        NULL DEFAULT NULL                           COMMENT '来源解析数据ID',
  `creator`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '创建者',
  `create_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP          COMMENT '创建时间',
  `updater`               varchar(64)   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''   COMMENT '更新者',
  `update_time`           datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`               bit(1)        NOT NULL DEFAULT b'0'                       COMMENT '是否删除',
  `tenant_id`             bigint        NOT NULL DEFAULT 0                          COMMENT '租户编号',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_source_parsed_data_id` (`source_parsed_data_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检食堂供应商信息表';

-- ================================================================
-- 执行验证语句（全部步骤完成后运行）：
-- SELECT TABLE_NAME, TABLE_COMMENT
-- FROM information_schema.TABLES
-- WHERE TABLE_SCHEMA = 'ruoyi_vue_pro'
--   AND TABLE_NAME IN (
--     'jijian_lessee','jijian_lease_contract','jijian_attendance_daily',
--     'jijian_leave_health','jijian_leave_personal','jijian_business_trip',
--     'jijian_compensatory_leave','jijian_canteen_supplier'
--   );
-- ================================================================
