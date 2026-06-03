package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 纪检考勤查询分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class JijianAttendancePageReqVO extends PageParam {

    @Schema(description = "数据类型，固定 ATTENDANCE", example = "ATTENDANCE")
    @NotBlank
    private String formType = "ATTENDANCE";

    @Schema(description = "部门，ALL=全单位，其余为具体部门名称", example = "ALL")
    @NotBlank
    private String department = "ALL";

    @Schema(description = "时间范围枚举值", example = "ONE_WEEK",
            allowableValues = {"ONE_DAY", "ONE_WEEK", "ONE_MONTH", "THREE_MONTHS", "HALF_YEAR", "ONE_YEAR"})
    @NotBlank
    private String timeRange = "ONE_WEEK";
}
