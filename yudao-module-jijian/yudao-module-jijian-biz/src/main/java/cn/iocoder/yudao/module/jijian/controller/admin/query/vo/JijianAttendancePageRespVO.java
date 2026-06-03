package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.service.query.dto.AttendanceSummaryDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 纪检考勤查询分页响应 VO")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JijianAttendancePageRespVO {

    @Schema(description = "分页数据")
    private PageResult<AttendanceItemVO> pageResult;

    @Schema(description = "聚合统计")
    private AttendanceSummaryDTO summary;

    @Schema(description = "考勤记录 VO")
    @Data
    public static class AttendanceItemVO {
        private Long id;
        private String employeeName;
        private String employeeNo;
        private String department;
        private LocalDate attendanceDate;
        private LocalDateTime checkinTime;
        private String checkinResult;
        private LocalDateTime checkoutTime;
        private String checkoutResult;
    }
}
