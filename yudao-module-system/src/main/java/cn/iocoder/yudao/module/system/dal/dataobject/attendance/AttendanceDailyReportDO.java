package cn.iocoder.yudao.module.system.dal.dataobject.attendance;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 考勤日报表 DO
 */
@TableName("attendance_daily_report")
@KeySequence("attendance_daily_report_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class AttendanceDailyReportDO extends TenantBaseDO {

    @TableId
    private Long id;
    /** 姓名 */
    private String employeeName;
    /** 员工编号 */
    private String employeeNo;
    /** 部门 */
    private String deptName;
    /** 考勤日期 */
    private LocalDate attendanceDate;
    /** 上班打卡时间 */
    private LocalDateTime clockInTime;
    /** 上班打卡结果 */
    private String clockInResult;
    /** 上班打卡地点 */
    private String clockInLocation;
    /** 上班备注 */
    private String clockInRemark;
    /** 下班打卡时间 */
    private LocalDateTime clockOutTime;
    /** 下班打卡结果 */
    private String clockOutResult;
    /** 下班打卡地点 */
    private String clockOutLocation;
    /** 下班备注 */
    private String clockOutRemark;

}
