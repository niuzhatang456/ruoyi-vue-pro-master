package cn.iocoder.yudao.module.system.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 智能查询考勤 Response VO")
@Data
public class AiQueryAttendanceRespVO {

    @Schema(description = "解析说明")
    private String parseSummary;
    @Schema(description = "数据表名")
    private String tableName;
    @Schema(description = "解析条件")
    private Map<String, Object> conditions;
    @Schema(description = "动态列")
    private List<AiQueryColumnVO> columns;
    @Schema(description = "查询结果")
    private List<AttendanceDailyReportRespVO> list;
    @Schema(description = "总数")
    private Long total;

}
