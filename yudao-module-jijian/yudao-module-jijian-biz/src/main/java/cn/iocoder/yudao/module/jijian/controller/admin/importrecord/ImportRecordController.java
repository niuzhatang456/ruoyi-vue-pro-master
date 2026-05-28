package cn.iocoder.yudao.module.jijian.controller.admin.importrecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.importrecord.vo.ImportRecordRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.service.importrecord.ImportRecordService;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.Arrays;
import java.util.List;
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

    @PostMapping("/ocr")
    @Operation(summary = "Upload OCR file and create an import record")
    public CommonResult<ImportRecordRespVO> uploadOcrFile(@RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "ocr");
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @PostMapping("/excel")
    @Operation(summary = "Upload Excel file and create an import record")
    public CommonResult<ImportRecordRespVO> uploadExcelFile(@RequestParam("file") MultipartFile file) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), "excel");
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @PostMapping("/drag")
    @Operation(summary = "Upload multiple files and create import records")
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

    @PostMapping("/upload")
    @Operation(summary = "Upload file and create an import record")
    public CommonResult<ImportRecordRespVO> uploadImportFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceType") @NotBlank String sourceType) {
        ImportRecordDO record = importRecordService.createImportRecord(file.getOriginalFilename(), sourceType);
        parsedDataService.parseAndCreate(record, file);
        return success(convert(record));
    }

    @GetMapping("/list")
    @Operation(summary = "Get recent import records")
    public CommonResult<List<ImportRecordRespVO>> getImportRecordList() {
        return success(importRecordService.getImportRecordList().stream()
                .map(this::convert)
                .collect(Collectors.toList()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get import record")
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
