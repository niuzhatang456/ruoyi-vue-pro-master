package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "纪检查询 - 指标卡片 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JijianMetricVO {

    @Schema(description = "指标 key")
    private String key;

    @Schema(description = "指标中文名称")
    private String label;

    @Schema(description = "指标值")
    private Object value;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "补充说明")
    private String description;
}
