package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "纪检查询 - 图表 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JijianChartVO {

    @Schema(description = "图表类型：pie / bar / line / table")
    private String type;

    @Schema(description = "图表标题")
    private String title;

    @Schema(description = "图表说明")
    private String description;

    @Schema(description = "X 轴标签（bar/line 使用）")
    private List<String> xAxis;

    @Schema(description = "系列数据（bar/line 使用）")
    private List<JijianChartSeriesVO> series;

    @Schema(description = "饼图/散点数据（pie 使用）")
    private List<Map<String, Object>> data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JijianChartSeriesVO {
        private String name;
        private List<Object> data;
    }
}
