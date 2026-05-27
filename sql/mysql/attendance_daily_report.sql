-- 考勤日报表
CREATE TABLE IF NOT EXISTS `attendance_daily_report` (
    `id`              bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `employee_name`   varchar(50)  NOT NULL COMMENT '姓名',
    `employee_no`     varchar(32)  NOT NULL COMMENT '员工编号',
    `dept_name`       varchar(100)          DEFAULT NULL COMMENT '部门',
    `attendance_date` date         NOT NULL COMMENT '考勤日期',
    `clock_in_time`   datetime              DEFAULT NULL COMMENT '上班打卡时间',
    `clock_in_result` varchar(32)           DEFAULT NULL COMMENT '上班打卡结果',
    `clock_in_location` varchar(200)        DEFAULT NULL COMMENT '上班打卡地点',
    `clock_in_remark` varchar(500)          DEFAULT NULL COMMENT '上班备注',
    `clock_out_time`  datetime              DEFAULT NULL COMMENT '下班打卡时间',
    `clock_out_result` varchar(32)          DEFAULT NULL COMMENT '下班打卡结果',
    `clock_out_location` varchar(200)        DEFAULT NULL COMMENT '下班打卡地点',
    `clock_out_remark` varchar(500)          DEFAULT NULL COMMENT '下班备注',
    `creator`         varchar(64)           DEFAULT '' COMMENT '创建者',
    `create_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater`         varchar(64)           DEFAULT '' COMMENT '更新者',
    `update_time`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         bit(1)       NOT NULL DEFAULT b'0' COMMENT '是否删除',
    `tenant_id`       bigint       NOT NULL DEFAULT 0 COMMENT '租户编号',
    PRIMARY KEY (`id`),
    KEY `idx_employee_no` (`employee_no`),
    KEY `idx_attendance_date` (`attendance_date`),
    KEY `idx_dept_name` (`dept_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='考勤日报表';

-- 示例数据（可按需调整或删除）
INSERT INTO `attendance_daily_report` (`employee_name`, `employee_no`, `dept_name`, `attendance_date`,
    `clock_in_time`, `clock_in_result`, `clock_in_location`, `clock_in_remark`,
    `clock_out_time`, `clock_out_result`, `clock_out_location`, `clock_out_remark`, `tenant_id`)
VALUES
('张三', '10001', '人事部', '2026-05-20', '2026-05-20 08:55:12', '正常', '总部大楼A座', '', '2026-05-20 18:02:33', '正常', '总部大楼A座', '', 1),
('李四', '10002', '人事部', '2026-05-20', '2026-05-20 09:18:45', '迟到', '总部大楼A座', '交通拥堵', '2026-05-20 18:10:00', '正常', '总部大楼A座', '', 1),
('王五', '10086', '财务部', '2026-05-19', '2026-05-19 08:50:00', '正常', '总部大楼B座', '', '2026-05-19 17:30:00', '早退', '总部大楼B座', '事假半天', 1),
('赵六', '10003', '市场部', '2026-05-13', '2026-05-13 09:05:00', '正常', '外地-上海办事处', '出差', '2026-05-13 18:00:00', '正常', '外地-上海办事处', '出差', 1),
('钱七', '10004', '人事部', '2026-05-19', '2026-05-19 09:25:00', '迟到', '总部大楼A座', '', '2026-05-19 18:05:00', '正常', '总部大楼A座', '', 1);
