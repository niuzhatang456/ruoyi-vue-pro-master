package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Schema(description = "纪检查询 - 过滤选项 VO（部门 / 月份 / 日期范围，均来自真实数据库数据）")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JijianQueryFilterOptionsVO {

    @Schema(description = "当前表存在的部门列表（从真实数据 distinct；无部门字段时为空列表）")
    private List<String> departments;

    @Schema(description = "当前表存在的月份列表（格式 yyyy-MM；无日期字段时为空列表）")
    private List<String> months;

    @Schema(description = "当前表数据的日期范围")
    private DateRange dateRange;

    @Schema(description = "当前表是否有部门字段")
    private boolean hasDepartment;

    @Schema(description = "当前表是否有时间字段")
    private boolean hasDateField;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        @Schema(description = "最早日期（yyyy-MM-dd）")
        private String min;
        @Schema(description = "最晚日期（yyyy-MM-dd）")
        private String max;
    }
}
