package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "纪检查询 - 数据类型 VO")
@Data
@AllArgsConstructor
public class JijianQueryFormTypeVO {

    @Schema(description = "枚举值", example = "ATTENDANCE")
    private String value;

    @Schema(description = "显示名称", example = "考勤信息")
    private String label;

    @Schema(description = "当前是否支持查询", example = "true")
    private boolean supported;
}
