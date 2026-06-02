package cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 纪检租赁合同 DO */
@TableName("jijian_lease_contract")
@KeySequence("jijian_lease_contract_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class LeaseContractDO extends TenantBaseDO {
    @TableId private Long id;
    private Long propertyId;
    private Long lesseeId;
    private LocalDateTime contractStartTime;
    private LocalDateTime contractEndTime;
    private BigDecimal amount;
    private String paymentStatus;
    private String waterElectricityMgmt;
    private String contractSummary;
    private String remark;
    private Long sourceParsedDataId;
}
