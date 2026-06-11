package cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 纪检考勤日报 DO */
@TableName("jijian_attendance_daily")
@KeySequence("jijian_attendance_daily_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class AttendanceDailyDO extends TenantBaseDO {
    @TableId private Long id;
    private String employeeName;
    private String employeeNo;
    private String department;
    private LocalDateTime checkinTime;
    private String checkinResult;
    private String checkinLocation;
    private String checkinRemark;
    private LocalDateTime checkoutTime;
    private String checkoutResult;
    private String checkoutLocation;
    private String checkoutRemark;
    private LocalDate attendanceDate;
    private String weekDay;
    private Long sourceParsedDataId;
}
