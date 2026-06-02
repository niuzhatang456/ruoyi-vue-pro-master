package cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 纪检疗休养请假 DO */
@TableName("jijian_leave_health")
@KeySequence("jijian_leave_health_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class LeaveHealthDO extends TenantBaseDO {
    @TableId private Long id;
    private String department;
    private String applicantName;
    private String employeeNo;
    private String leaveLocation;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private String workYears;
    private LocalDateTime startWorkTime;
    private String remark;
    private Long sourceParsedDataId;
}
