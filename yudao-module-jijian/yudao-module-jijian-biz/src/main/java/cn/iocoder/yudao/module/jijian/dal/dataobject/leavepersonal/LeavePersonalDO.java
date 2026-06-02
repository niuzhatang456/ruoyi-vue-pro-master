package cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 纪检事假 DO */
@TableName("jijian_leave_personal")
@KeySequence("jijian_leave_personal_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class LeavePersonalDO extends TenantBaseDO {
    @TableId private Long id;
    private String department;
    private String applicantName;
    private String employeeNo;
    private String leaveType;
    private String leaveReason;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal leaveDays;
    private Boolean isOutside;
    private String outsideLocation;
    private String leaveStatus;
    private String leaveMonth;
    private String remark;
    private Long sourceParsedDataId;
}
