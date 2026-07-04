package cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 纪检租赁合同 DO */
@TableName("jijian_lease_contract")
@KeySequence("jijian_lease_contract_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class LeaseContractDO extends TenantBaseDO {
    @TableId private Long id;
    @TableField(exist = false)
    private Long propertyId;
    @TableField(exist = false)
    private Long lesseeId;
    /** 合同编号 */
    private String contractNo;
    /** 甲方 */
    @TableField(exist = false)
    private String partyA;
    /** 乙方/承租人姓名 */
    private String lesseeName;
    /** 合同签订日期 */
    private LocalDate contractSignDate;
    /** 出租方 */
    private String lessorName;
    /** 承租人身份证号 */
    private String lesseeIdCard;
    /** 承租人联系电话 */
    private String lesseePhone;
    /** 房屋状况 */
    private String houseCondition;
    /** 租赁开始日期 */
    private LocalDate leaseStartDate;
    /** 租赁结束日期 */
    private LocalDate leaseEndDate;
    /** 租赁年份 */
    private String leaseYears;
    /** 租赁用途 */
    private String leasePurpose;
    /** 多条租金及交纳日期 JSON */
    private String rentInfoJson;
    /** 保证金/押金/履约保证金 */
    private String deposit;
    /** 水费条款摘要 */
    private String waterFee;
    /** 电费条款摘要 */
    private String electricityFee;
    /** 合同原件文件名 */
    private String originalFileName;
    /** 合同原件访问地址 */
    private String originalFileUrl;
    /** 合同原件保存路径 */
    private String originalFilePath;
    /** OCR 原文 */
    private String ocrRawText;
    /** 解析状态 */
    private String parseStatus;
    /** 解析错误/提示 */
    private String parseErrorMsg;
    /** 合同时间（签订/记载时间，区别于合同起止时间） */
    @TableField(exist = false)
    private LocalDateTime contractTime;
    @TableField(exist = false)
    private LocalDateTime contractStartTime;
    @TableField(exist = false)
    private LocalDateTime contractEndTime;
    @TableField(exist = false)
    private BigDecimal amount;
    @TableField(exist = false)
    private String paymentStatus;
    @TableField(exist = false)
    private String waterElectricityMgmt;
    @TableField(exist = false)
    private String contractSummary;
    private String remark;
    private Long sourceParsedDataId;

    public String getPartyA() {
        return StrUtil.blankToDefault(partyA, lessorName);
    }

    public LocalDateTime getContractTime() {
        return contractTime != null ? contractTime : toDateTime(contractSignDate);
    }

    public LocalDateTime getContractStartTime() {
        return contractStartTime != null ? contractStartTime : toDateTime(leaseStartDate);
    }

    public LocalDateTime getContractEndTime() {
        return contractEndTime != null ? contractEndTime : toDateTime(leaseEndDate);
    }

    public BigDecimal getAmount() {
        if (amount != null) {
            return amount;
        }
        String rentText = firstRentJsonValue("rentText", "rentAmount");
        if (StrUtil.isBlank(rentText)) {
            return null;
        }
        Matcher matcher = Pattern.compile("([0-9]+(?:[,，][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+)\\s*元?").matcher(rentText);
        if (!matcher.find()) {
            return null;
        }
        try {
            return new BigDecimal(matcher.group(1).replace("，", ",").replace(",", ""));
        } catch (Exception ignored) {
            return null;
        }
    }

    public String getPaymentStatus() {
        return StrUtil.blankToDefault(paymentStatus, firstRentJsonValue("paymentText", "paymentDate"));
    }

    public String getWaterElectricityMgmt() {
        if (StrUtil.isNotBlank(waterElectricityMgmt)) {
            return waterElectricityMgmt;
        }
        if (StrUtil.isBlank(waterFee)) {
            return electricityFee;
        }
        if (StrUtil.isBlank(electricityFee)) {
            return waterFee;
        }
        return "水费：" + waterFee + "；电费：" + electricityFee;
    }

    public String getContractSummary() {
        if (StrUtil.isNotBlank(contractSummary)) {
            return contractSummary;
        }
        StringBuilder sb = new StringBuilder();
        if (StrUtil.isNotBlank(houseCondition)) {
            sb.append("房屋状况：").append(houseCondition);
        }
        if (StrUtil.isNotBlank(leasePurpose)) {
            if (sb.length() > 0) sb.append("；");
            sb.append("用途：").append(leasePurpose);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private LocalDateTime toDateTime(LocalDate value) {
        return value == null ? null : value.atStartOfDay();
    }

    private String firstRentJsonValue(String primaryKey, String fallbackKey) {
        if (StrUtil.isBlank(rentInfoJson)) {
            return "";
        }
        try {
            JSONArray array = JSONUtil.parseArray(rentInfoJson);
            if (array.isEmpty()) {
                return "";
            }
            JSONObject first = array.getJSONObject(0);
            String value = first.getStr(primaryKey);
            return StrUtil.blankToDefault(value, first.getStr(fallbackKey, ""));
        } catch (Exception ignored) {
            return "";
        }
    }
}
