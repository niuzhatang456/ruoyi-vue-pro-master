-- 纪检系统 房产表 UNIQUE 约束修复
-- 问题：jijian_property.uk_source_parsed_data_id 是 UNIQUE 约束，
--       但单次 Excel 导入含多行房产时（source_parsed_data_id 相同），第 2 条起报 Duplicate entry 错误。
-- 修复：改为普通索引，幂等防重由 parsed_data.status 层面控制。
--
-- 执行前先确认已连接正确数据库（use ruoyi-vue-pro; 或对应库名）

ALTER TABLE jijian_property DROP INDEX uk_source_parsed_data_id;
ALTER TABLE jijian_property ADD INDEX idx_source_parsed_data_id (source_parsed_data_id);
