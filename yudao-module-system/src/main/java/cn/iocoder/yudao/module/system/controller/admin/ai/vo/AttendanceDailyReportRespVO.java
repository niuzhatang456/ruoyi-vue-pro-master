package cn.iocoder.yudao.module.system.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "考勤日报表记录")
@Data
public class AttendanceDailyReportRespVO {

    @Schema(description = "姓名")
    private String employeeName;
    @Schema(description = "员工编号")
    private String employeeNo;
    @Schema(description = "部门")
    private String deptName;
    @Schema(description = "上班打卡时间")
    private String clockInTime;
    @Schema(description = "上班打卡结果")
    private String clockInResult;
    @Schema(description = "上班打卡地点")
    private String clockInLocation;
    @Schema(description = "上班备注")
    private String clockInRemark;
    @Schema(description = "下班打卡时间")
    private String clockOutTime;
    @Schema(description = "下班打卡结果")
    private String clockOutResult;
    @Schema(description = "下班打卡地点")
    private String clockOutLocation;
    @Schema(description = "下班备注")
    private String clockOutRemark;

}
