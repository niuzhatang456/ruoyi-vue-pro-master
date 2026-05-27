-- ----------------------------
-- Table structure for jijian_property
-- ----------------------------
DROP TABLE IF EXISTS `jijian_property`;
CREATE TABLE `jijian_property`  (
  `id`               bigint          NOT NULL AUTO_INCREMENT                                              COMMENT '主键编号',
  `property_address` varchar(255)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL           COMMENT '房产地址',
  `property_name`    varchar(128)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL           COMMENT '房产名称',
  `ownership_info`   varchar(255)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL           COMMENT '产权信息',
  `building_time`    datetime        NULL DEFAULT NULL                                                   COMMENT '建筑时间',
  `area`             decimal(10, 2)  NULL DEFAULT NULL                                                   COMMENT '建筑面积（平方米）',
  `lease_status`     varchar(64)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL  COMMENT '租赁情况',
  `remark`           varchar(500)    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL  COMMENT '备注',
  `creator`          varchar(64)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''    COMMENT '创建者',
  `create_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP                                  COMMENT '创建时间',
  `updater`          varchar(64)     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT ''    COMMENT '更新者',
  `update_time`      datetime        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP      COMMENT '更新时间',
  `deleted`          bit(1)          NOT NULL DEFAULT b'0'                                               COMMENT '是否删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '纪检房产情况表';
