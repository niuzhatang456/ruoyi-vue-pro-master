package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 解析数据确认写入房产 Response VO")
@Data
public class ConfirmPropertyRespVO {

    @Schema(description = "写入的正式房产记录 ID", example = "1024")
    private Long propertyId;

    @Schema(description = "解析数据 ID", example = "5")
    private Long parsedDataId;

}
