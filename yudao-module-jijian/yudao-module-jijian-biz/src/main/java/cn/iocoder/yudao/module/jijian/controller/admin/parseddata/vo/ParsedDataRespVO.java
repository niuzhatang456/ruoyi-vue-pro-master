package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Schema(description = "Admin - 纪检解析数据 Response VO")
@Data
public class ParsedDataRespVO {

    @Schema(description = "解析记录ID")
    private Long id;

    @Schema(description = "导入记录ID")
    private Long importRecordId;

    @Schema(description = "文件名")
    private String fileName;

    @Schema(description = "来源类型", example = "excel/ocr/drag")
    private String sourceType;

    @Schema(description = "导入记录检测到的表单类型")
    private String detectedFormType;

    @Schema(description = "解析识别到的表单业务类型", example = "房产信息")
    private String formType;

    @Schema(description = "OCR / 解析摘要文本")
    private String rawText;

    @Schema(description = "解析结构化数据 JSON（headers + rows）")
    private String parsedJson;

    @Schema(description = "用户校正 JSON（优先于 parsedJson 参与确认写入）")
    private String correctedJson;

    @Schema(description = "识别置信度")
    private BigDecimal confidence;

    @Schema(description = "解析状态：success（解析成功）/ failed（解析失败）")
    private String status;

    @Schema(description = "确认状态：pending（待确认）/ confirmed（已确认写入正式表）")
    private String confirmStatus;

    @Schema(description = "错误信息（解析失败或 OCR 未启动时有值）")
    private String errorMsg;

    @Schema(description = "确认写入时间（ISO-8601）")
    private String confirmTime;

    @Schema(description = "写入的正式业务表名", example = "jijian_canteen_supplier")
    private String businessTable;

    @Schema(description = "写入的正式业务记录 ID 列表（JSON 数组）", example = "[1,2,3]")
    private String businessIds;

    @Schema(description = "确认写入后的单条房产记录ID（兼容旧字段）")
    private Long confirmedPropertyId;

    @Schema(description = "创建时间")
    private String createdAt;
}
