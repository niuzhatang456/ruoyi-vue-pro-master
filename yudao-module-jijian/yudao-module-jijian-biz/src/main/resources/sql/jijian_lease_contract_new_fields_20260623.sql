-- 租赁合同 OCR 识别入库字段扩展：仅新增字段，不修改/删除历史字段
ALTER TABLE jijian_lease_contract
    ADD COLUMN contract_sign_date date NULL COMMENT '合同签订日期' AFTER contract_no,
    ADD COLUMN lessor_name varchar(255) NULL COMMENT '出租方' AFTER party_a,
    ADD COLUMN lessee_id_card varchar(20) NULL COMMENT '承租人身份证号' AFTER lessee_name,
    ADD COLUMN lessee_phone varchar(64) NULL COMMENT '承租人联系电话' AFTER lessee_id_card,
    ADD COLUMN house_condition text NULL COMMENT '房屋状况' AFTER lessee_phone,
    ADD COLUMN lease_start_date date NULL COMMENT '租赁开始日期' AFTER house_condition,
    ADD COLUMN lease_end_date date NULL COMMENT '租赁结束日期' AFTER lease_start_date,
    ADD COLUMN lease_purpose varchar(255) NULL COMMENT '租赁用途' AFTER lease_end_date,
    ADD COLUMN rent_info_json longtext NULL COMMENT '租金及交纳日期 JSON' AFTER lease_purpose,
    ADD COLUMN deposit varchar(255) NULL COMMENT '保证金/押金/履约保证金' AFTER rent_info_json,
    ADD COLUMN water_fee varchar(255) NULL COMMENT '水费条款摘要' AFTER deposit,
    ADD COLUMN electricity_fee varchar(255) NULL COMMENT '电费条款摘要' AFTER water_fee,
    ADD COLUMN original_file_name varchar(255) NULL COMMENT '合同原件文件名' AFTER source_parsed_data_id,
    ADD COLUMN original_file_url varchar(500) NULL COMMENT '合同原件访问地址' AFTER original_file_name,
    ADD COLUMN original_file_path varchar(500) NULL COMMENT '合同原件保存路径' AFTER original_file_url,
    ADD COLUMN ocr_raw_text longtext NULL COMMENT 'OCR 原文' AFTER original_file_path,
    ADD COLUMN parse_status varchar(32) NULL COMMENT '合同解析状态' AFTER ocr_raw_text,
    ADD COLUMN parse_error_msg varchar(500) NULL COMMENT '合同解析错误/提示' AFTER parse_status;

CREATE INDEX idx_jijian_lease_contract_no ON jijian_lease_contract (contract_no);
CREATE INDEX idx_jijian_lease_contract_sign_date ON jijian_lease_contract (contract_sign_date);
CREATE INDEX idx_jijian_lease_contract_lease_date ON jijian_lease_contract (lease_start_date, lease_end_date);
