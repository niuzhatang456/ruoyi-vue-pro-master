package cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;

/** 纪检调休 DO */
@TableName("jijian_compensatory_leave")
@KeySequence("jijian_compensatory_leave_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class CompensatoryLeaveDO extends TenantBaseDO {
    @TableId private Long id;
    private String applicantName;
    private String employeeNo;
    private String department;
    private LocalDateTime overtimeStartTime;
    private String overtimeStartShift;
    private LocalDateTime overtimeEndTime;
    private String overtimeEndShift;
    private LocalDateTime compensatoryStartTime;
    private String compensatoryStartShift;
    private LocalDateTime compensatoryEndTime;
    private String compensatoryEndShift;
    private String compensatoryDuration;
    private String isOutside;         // 原文：出义/不出义/是/否
    private String outsideLocation;
    private String remark;
    private Long sourceParsedDataId;
}
