package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

@Schema(description = "纪检查询 - 部门选项 VO")
@Data
@AllArgsConstructor
public class JijianQueryDepartmentVO {

    @Schema(description = "部门值，ALL 表示全单位", example = "ALL")
    private String value;

    @Schema(description = "显示名称", example = "全单位")
    private String label;
}
