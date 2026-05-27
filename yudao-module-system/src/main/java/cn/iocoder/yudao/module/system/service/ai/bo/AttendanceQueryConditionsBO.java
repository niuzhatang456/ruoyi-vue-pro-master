package cn.iocoder.yudao.module.system.service.ai.bo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 考勤智能查询 - 解析后的查询条件
 */
@Data
public class AttendanceQueryConditionsBO {

    private String employeeName;
    private String employeeNo;
    private String deptName;
    private String clockInResultLike;
    private String clockOutResultLike;
    private String remarkKeyword;
    private LocalDate dateStart;
    private LocalDate dateEnd;

}
