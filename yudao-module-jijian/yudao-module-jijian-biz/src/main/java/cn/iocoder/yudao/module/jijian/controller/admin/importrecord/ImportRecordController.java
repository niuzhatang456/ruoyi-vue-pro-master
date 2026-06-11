package cn.iocoder.yudao.module.jijian.controller.admin.importrecord;

import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.importrecord.vo.ImportRecordRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.service.importrecord.ImportRecordService;
import cn.iocoder.yudao.module.jijian.service.parseddata.JijianFormTypeAutoDetectService;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Admin - Jijian import records")
@RestController
@RequestMapping("/jijian/import")
@Validated
public class ImportRecordController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ImportRecordService importRecordService;
    @Resource
    private ParsedDataService parsedDataService;
    @Resource
    private JijianFormTypeAutoDetectService formTypeAutoDetectService;

    @PostMapping("/ocr")
    @Operation(summary = "Upload OCR file and create an import record")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    public CommonResult<ImportRecordRespVO> uploadOcrFile(@RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "ocr");
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @PostMapping("/excel")
    @Operation(summary = "Upload Excel file and create an import record")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    public CommonResult<ImportRecordRespVO> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "excel");
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @PostMapping("/drag")
    @Operation(summary = "Upload multiple files and create import records")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    public CommonResult<List<ImportRecordRespVO>> uploadDragFiles(@RequestParam("files") MultipartFile[] files) {
        return success(Arrays.stream(files)
                .map(file -> {
                    ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "drag");
                    parsedDataService.parseAndCreate(record, file);
                    return record;
                })
                .map(this::convert)
                .collect(Collectors.toList()));
    }

    /**
     * 拖拽录入：上传单个文件，自动解析并返回识别结果（含置信度、命中表头）。
     * 前端用此端点替代旧 /drag 接口，以获取 formType 置信度信息。
     */
    @PostMapping("/drag/detect")
    @Operation(summary = "拖拽上传：自动识别表单类型（含置信度）")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    public CommonResult<DragDetectRespVO> uploadDragDetect(@RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "drag");
        ParsedDataDO parsedData = parsedDataService.parseAndCreate(record, file);

        // 从 parsedJson 中提取表头用于置信度计算
        List<String> headers = extractHeaders(parsedData);
        String sheetName = extractSheetName(parsedData);

        JijianFormTypeAutoDetectService.DetectResult detectResult =
                formTypeAutoDetectService.detect(file.getOriginalFilename(), sheetName, headers);

        DragDetectRespVO respVO = new DragDetectRespVO();
        boolean parseFailed = parsedData == null || "failed".equals(parsedData.getStatus());
        respVO.setImportRecordId(record.getId());
        // 解析失败时不暴露 parsedDataId，避免前端显示"选择类型"等操作按钮
        respVO.setParsedDataId(parseFailed ? null : parsedData.getId());
        respVO.setFileName(file.getOriginalFilename());
        respVO.setDetectedFormType(detectResult.detectedFormType != null
                ? detectResult.detectedFormType
                : (parsedData != null ? parsedData.getFormType() : null));
        respVO.setDetectedFormName(detectResult.detectedFormName);
        respVO.setConfidence(detectResult.confidence);
        respVO.setMatchedHeaders(detectResult.matchedHeaders);
        respVO.setNeedsConfirmation(detectResult.needsConfirmation || parseFailed);
        respVO.setParseStatus(parsedData != null ? parsedData.getStatus() : "failed");
        respVO.setErrorMsg(parsedData != null ? parsedData.getErrorMsg() : "文件解析失败");
        // Sheet 信息（仅 Excel 有意义）
        respVO.setSheetName(extractSheetName(parsedData));
        respVO.setSheetCount(extractSheetCount(parsedData));
        // 候选列表
        List<DragDetectRespVO.CandidateVO> candidates = new ArrayList<>();
        for (JijianFormTypeAutoDetectService.CandidateScore c : detectResult.candidateTypes) {
            DragDetectRespVO.CandidateVO cv = new DragDetectRespVO.CandidateVO();
            cv.setFormType(c.formType);
            cv.setDisplayName(c.displayName);
            cv.setConfidence(c.confidence);
            candidates.add(cv);
        }
        respVO.setCandidateTypes(candidates);
        return success(respVO);
    }

    /**
     * 重新以指定 formType 解析（用于用户手工选择表单类型后触发）。
     * 会覆盖旧的 parsedData（在同一 importRecordId 下新建一条 parsedData）。
     */
    @PostMapping("/drag/reparse/{importRecordId}")
    @Operation(summary = "重新解析：以用户指定的表单类型重新解析文件")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    @Parameter(name = "importRecordId", description = "导入记录 ID", required = true)
    public CommonResult<DragDetectRespVO> reparseWithFormType(
            @PathVariable("importRecordId") Long importRecordId,
            @RequestParam("formType") @NotBlank String formType,
            @RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.getImportRecord(importRecordId);
        if (record == null) {
            return success(null);
        }
        ParsedDataDO parsedData = parsedDataService.parseAndCreateWithFormType(record, file, formType);

        DragDetectRespVO respVO = new DragDetectRespVO();
        respVO.setImportRecordId(record.getId());
        respVO.setParsedDataId(parsedData != null ? parsedData.getId() : null);
        respVO.setFileName(file.getOriginalFilename());
        respVO.setDetectedFormType(formType);
        respVO.setConfidence(1.0); // 用户手工确认，置信度设为 1.0
        respVO.setMatchedHeaders(Collections.emptyList());
        respVO.setNeedsConfirmation(false);
        respVO.setParseStatus(parsedData != null ? parsedData.getStatus() : "failed");
        respVO.setErrorMsg(parsedData != null ? parsedData.getErrorMsg() : null);
        respVO.setCandidateTypes(Collections.emptyList());
        return success(respVO);
    }

    // ─── 内部辅助 ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<String> extractHeaders(ParsedDataDO pd) {
        if (pd == null || pd.getParsedJson() == null) return Collections.emptyList();
        try {
            cn.hutool.json.JSONObject json = JSONUtil.parseObj(pd.getParsedJson());
            Object h = json.get("headers");
            if (h instanceof List) return (List<String>) h;
        } catch (Exception ignore) {}
        return Collections.emptyList();
    }

    private String extractSheetName(ParsedDataDO pd) {
        if (pd == null || pd.getParsedJson() == null) return null;
        try {
            cn.hutool.json.JSONObject json = JSONUtil.parseObj(pd.getParsedJson());
            return json.getStr("sheetName");
        } catch (Exception ignore) {}
        return null;
    }

    private int extractSheetCount(ParsedDataDO pd) {
        if (pd == null || pd.getParsedJson() == null) return 1;
        try {
            cn.hutool.json.JSONObject json = JSONUtil.parseObj(pd.getParsedJson());
            Integer count = json.getInt("sheetCount");
            return count != null ? count : 1;
        } catch (Exception ignore) {}
        return 1;
    }

    /** 拖拽检测响应 VO */
    @Data
    public static class DragDetectRespVO {
        private Long importRecordId;
        private Long parsedDataId;
        private String fileName;
        private String detectedFormType;
        private String detectedFormName;
        private double confidence;
        private List<String> matchedHeaders;
        private boolean needsConfirmation;
        private String parseStatus;
        private String errorMsg;
        private List<CandidateVO> candidateTypes;
        /** Excel 当前使用的 Sheet 名；CSV/OCR 为 null */
        private String sheetName;
        /** Excel 文件的 Sheet 总数；非 Excel 或单 Sheet 均为 1 */
        private int sheetCount = 1;

        @Data
        public static class CandidateVO {
            private String formType;
            private String displayName;
            private double confidence;
        }
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload file and create an import record")
    @PreAuthorize("@ss.hasPermission('jijian:import:upload')")
    public CommonResult<ImportRecordRespVO> uploadImportFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") @NotBlank String sourceType) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), sourceType);
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @GetMapping("/list")
    @Operation(summary = "Get recent import records")
    @PreAuthorize("@ss.hasPermission('jijian:import:query')")
    public CommonResult<List<ImportRecordRespVO>> getImportRecordList() {
        return success(importRecordService.getImportRecordList().stream()
                .map(this::convert)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import record")
    @PreAuthorize("@ss.hasPermission('jijian:import:query')")
    @Parameter(name = "id", description = "Record id", required = true)
    public CommonResult<ImportRecordRespVO> getImportRecord(@PathVariable("id") Long id) {
        return success(convert(importRecordService.getImportRecord(id)));
    }

    private ImportRecordRespVO convert(ImportRecordDO record) {
        if (record == null) {
            return null;
        }
        ImportRecordRespVO respVO = new ImportRecordRespVO();
        respVO.setId(record.getId());
        respVO.setFileName(record.getFileName());
        respVO.setSourceType(record.getSourceType());
        respVO.setDetectedFormType(record.getDetectedFormType());
        respVO.setStatus(record.getStatus());
        respVO.setCreatedAt(formatCreatedAt(record));
        return respVO;
    }

    private String formatCreatedAt(ImportRecordDO record) {
        LocalDateTime createdAt = record.getCreatedAt() != null ? record.getCreatedAt() : record.getCreateTime();
        return createdAt == null ? null : DATE_TIME_FORMATTER.format(createdAt);
    }

}
