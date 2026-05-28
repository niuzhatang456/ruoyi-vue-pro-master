-- ----------------------------
-- Table structure for jijian_import_parsed_data
-- ----------------------------
DROP TABLE IF EXISTS `jijian_import_parsed_data`;
CREATE TABLE `jijian_import_parsed_data` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'primary id',
  `import_record_id` bigint NOT NULL COMMENT 'jijian import record id',
  `form_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'detected form type',
  `raw_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci COMMENT 'raw text',
  `parsed_json` json COMMENT 'parsed structured json',
  `confidence` decimal(5, 4) DEFAULT NULL COMMENT 'parse confidence',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'pending / success / failed',
  `error_msg` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'error message',
  `creator` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'creator',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `updater` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT 'updater',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT 'deleted',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT 'tenant id',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_import_record_id` (`import_record_id`) USING BTREE
) ENGINE = InnoDB
  AUTO_INCREMENT = 1
  CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'jijian import parsed data';
