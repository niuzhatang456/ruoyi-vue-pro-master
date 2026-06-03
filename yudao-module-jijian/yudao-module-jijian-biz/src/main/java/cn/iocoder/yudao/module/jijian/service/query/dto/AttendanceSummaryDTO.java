package cn.iocoder.yudao.module.jijian.service.query.dto;

import lombok.Data;

import java.util.List;

/** 考勤聚合统计结果 */
@Data
public class AttendanceSummaryDTO {

    /** 总记录数 */
    private long totalCount;

    /** 涉及部门数 */
    private long departmentCount;

    /** 按部门统计：[{department, count}] */
    private List<DepartmentCountDTO> byDepartment;

    /** 按签到结果统计：[{status, count}] */
    private List<StatusCountDTO> byAttendanceStatus;

    @Data
    public static class DepartmentCountDTO {
        private String department;
        private long count;
    }

    @Data
    public static class StatusCountDTO {
        /** checkinResult 原始值，TODO: 后续增加标准化状态字段后可进一步分类 */
        private String status;
        private long count;
    }
}
