-- 租赁合同入库完整性修复：预览中的 合同编号/甲方/乙方 此前被静默丢弃（表无对应列）
ALTER TABLE jijian_lease_contract
    ADD COLUMN contract_no VARCHAR(64) NULL COMMENT '合同编号' AFTER lessee_id,
    ADD COLUMN party_a VARCHAR(255) NULL COMMENT '甲方' AFTER contract_no,
    ADD COLUMN lessee_name VARCHAR(255) NULL COMMENT '乙方/承租人姓名' AFTER party_a;
