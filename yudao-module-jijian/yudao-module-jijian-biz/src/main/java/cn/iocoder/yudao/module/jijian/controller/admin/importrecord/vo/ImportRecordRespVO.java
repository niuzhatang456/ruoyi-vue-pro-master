package cn.iocoder.yudao.module.jijian.controller.admin.importrecord.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "Admin - Jijian import record response")
@Data
public class ImportRecordRespVO {

    @Schema(description = "Record id", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long id;

    @Schema(description = "File name", example = "demo.xlsx")
    private String fileName;

    @Schema(description = "Source type", example = "excel")
    private String sourceType;

    @Schema(description = "Detected form type", example = "考勤信息")
    private String detectedFormType;

    @Schema(description = "Status", example = "success")
    private String status;

    @Schema(description = "Created time")
    private String createdAt;

}
