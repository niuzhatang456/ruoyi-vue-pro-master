package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 保存解析结果人工校正 Request VO")
@Data
public class SaveCorrectionReqVO {

    @Schema(description = "用户校正后的 JSON 字符串（与 parsedJson 格式相同）", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "校正 JSON 不能为空")
    private String correctedJson;

}
