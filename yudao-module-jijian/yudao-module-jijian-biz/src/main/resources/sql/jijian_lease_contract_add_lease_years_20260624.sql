-- 租赁合同：新增租赁年份字段。仅新增字段，不修改历史 SQL。
ALTER TABLE jijian_lease_contract
    ADD COLUMN lease_years varchar(64) NULL COMMENT '租赁年份' AFTER lease_end_date;
