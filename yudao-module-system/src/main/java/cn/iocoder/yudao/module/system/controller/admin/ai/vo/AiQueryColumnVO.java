package cn.iocoder.yudao.module.system.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "智能查询 - 表格列")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiQueryColumnVO {

    @Schema(description = "字段名")
    private String field;
    @Schema(description = "列标题")
    private String label;
    @Schema(description = "列宽")
    private Integer width;
    @Schema(description = "最小列宽")
    private Integer minWidth;
    @Schema(description = "对齐方式")
    private String align;

}
