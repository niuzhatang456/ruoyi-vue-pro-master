package cn.iocoder.yudao.module.system.controller.admin.ai.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 智能查询考勤 Request VO")
@Data
public class AiQueryAttendanceReqVO {

    @Schema(description = "自然语言查询内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "查询张三本月考勤")
    @NotBlank(message = "查询内容不能为空")
    private String message;

}
