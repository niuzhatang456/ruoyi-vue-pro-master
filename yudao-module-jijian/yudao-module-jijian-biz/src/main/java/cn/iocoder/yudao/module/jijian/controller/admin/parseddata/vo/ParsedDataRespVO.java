package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "Admin - Jijian parsed data response")
@Data
public class ParsedDataRespVO {

    @Schema(description = "Parsed data id", example = "1")
    private Long id;

    @Schema(description = "Import record id", example = "1")
    private Long importRecordId;

    @Schema(description = "File name", example = "demo.xlsx")
    private String fileName;

    @Schema(description = "Source type", example = "excel")
    private String sourceType;

    @Schema(description = "Detected form type", example = "考勤信息")
    private String detectedFormType;

    @Schema(description = "Form type", example = "考勤信息")
    private String formType;

    @Schema(description = "Raw parsed text")
    private String rawText;

    @Schema(description = "Parsed JSON")
    private String parsedJson;

    @Schema(description = "Confidence")
    private BigDecimal confidence;

    @Schema(description = "Parse status", example = "success")
    private String status;

    @Schema(description = "Error message")
    private String errorMsg;

    @Schema(description = "Created time")
    private String createdAt;

}
