package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

/**
 * 通用分页查询请求 VO。
 *
 * <p>用于 POST /admin-api/jijian/query/page，支持多种 formType 路由到对应 Handler。
 */
@Schema(description = "管理后台 - 纪检通用分页查询 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JijianQueryPageReqVO extends PageParam {

    @Schema(description = "数据类型", example = "ATTENDANCE", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "数据类型不能为空")
    private String formType;

    @Schema(description = "部门（ALL=全单位），对无部门字段的表单类型该字段忽略", example = "ALL")
    private String department = "ALL";

    @Schema(description = "时间范围枚举值", example = "ONE_MONTH")
    private String timeRange = "ONE_MONTH";
}
