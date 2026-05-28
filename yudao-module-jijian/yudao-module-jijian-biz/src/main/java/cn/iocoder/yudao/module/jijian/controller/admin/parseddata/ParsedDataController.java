package cn.iocoder.yudao.module.jijian.controller.admin.parseddata;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.ParsedDataRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.service.importrecord.ImportRecordService;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Admin - Jijian parsed data")
@RestController
@RequestMapping("/jijian/import/parsed")
@Validated
public class ParsedDataController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private ParsedDataService parsedDataService;
    @Resource
    private ImportRecordService importRecordService;

    @GetMapping("/{importRecordId}")
    @Operation(summary = "Get parsed data by import record id")
    @Parameter(name = "importRecordId", description = "Import record id", required = true)
    public CommonResult<ParsedDataRespVO> getParsedData(@PathVariable("importRecordId") Long importRecordId) {
        return success(convert(parsedDataService.getLatestParsedData(importRecordId)));
    }

    @GetMapping("/list")
    @Operation(summary = "Get parsed data list")
    public CommonResult<List<ParsedDataRespVO>> getParsedDataList(@RequestParam(value = "importRecordId", required = false) Long importRecordId) {
        return success(parsedDataService.getParsedDataList(importRecordId).stream()
                .map(this::convert)
                .collect(Collectors.toList()));
    }

    private ParsedDataRespVO convert(ParsedDataDO parsedData) {
        if (parsedData == null) {
            return null;
        }
        ImportRecordDO importRecord = importRecordService.getImportRecord(parsedData.getImportRecordId());
        ParsedDataRespVO respVO = new ParsedDataRespVO();
        respVO.setId(parsedData.getId());
        respVO.setImportRecordId(parsedData.getImportRecordId());
        respVO.setFormType(parsedData.getFormType());
        respVO.setRawText(parsedData.getRawText());
        respVO.setParsedJson(parsedData.getParsedJson());
        respVO.setConfidence(parsedData.getConfidence());
        respVO.setStatus(parsedData.getStatus());
        respVO.setErrorMsg(parsedData.getErrorMsg());
        respVO.setCreatedAt(formatCreatedAt(parsedData.getCreateTime()));
        if (importRecord != null) {
            respVO.setFileName(importRecord.getFileName());
            respVO.setSourceType(importRecord.getSourceType());
            respVO.setDetectedFormType(importRecord.getDetectedFormType());
        }
        return respVO;
    }

    private String formatCreatedAt(LocalDateTime createdAt) {
        return createdAt == null ? null : DATE_TIME_FORMATTER.format(createdAt);
    }

}
