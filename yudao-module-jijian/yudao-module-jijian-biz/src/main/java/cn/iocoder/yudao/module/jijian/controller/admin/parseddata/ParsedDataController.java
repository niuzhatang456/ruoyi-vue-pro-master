package cn.iocoder.yudao.module.jijian.controller.admin.parseddata;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.ConfirmPropertyRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.DeleteBusinessDataRespVO;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService.DeleteBusinessDataResult;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.ConfirmWriteResultRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.ParsedDataRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo.SaveCorrectionReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.service.importrecord.ImportRecordService;
import cn.iocoder.yudao.module.jijian.service.parseddata.ParsedDataService;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.CREATE;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.UPDATE;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "Admin - 纪检解析数据")
@RestController
@RequestMapping("/jijian/import/parsed")
@Validated
public class ParsedDataController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource private ParsedDataService  parsedDataService;
    @Resource private ImportRecordService importRecordService;

    // ─── 查询 ──────────────────────────────────────────────────────────────

    @GetMapping("/{importRecordId}")
    @Operation(summary = "按导入记录 ID 查最新解析结果")
    @PreAuthorize("@ss.hasPermission('jijian:import:query')")
    @Parameter(name = "importRecordId", description = "导入记录 ID", required = true)
    public CommonResult<ParsedDataRespVO> getParsedData(@PathVariable("importRecordId") Long importRecordId) {
        return success(convert(parsedDataService.getLatestParsedData(importRecordId)));
    }

    @GetMapping("/list")
    @Operation(summary = "按导入记录 ID 查全部解析结果")
    @PreAuthorize("@ss.hasPermission('jijian:import:query')")
    public CommonResult<List<ParsedDataRespVO>> getParsedDataList(
            @RequestParam(value = "importRecordId", required = false) Long importRecordId) {
        return success(parsedDataService.getParsedDataList(importRecordId).stream()
                .map(this::convert).collect(Collectors.toList()));
    }

    @GetMapping("/detail/{parsedDataId}")
    @Operation(summary = "按解析记录 ID 查单条解析结果")
    @PreAuthorize("@ss.hasPermission('jijian:import:query')")
    @Parameter(name = "parsedDataId", description = "解析数据 ID", required = true)
    public CommonResult<ParsedDataRespVO> getParsedDataDetail(@PathVariable("parsedDataId") Long parsedDataId) {
        return success(convert(parsedDataService.getParsedDataById(parsedDataId)));
    }

    // ─── 校正 ──────────────────────────────────────────────────────────────

    @PutMapping("/{parsedDataId}/correction")
    @Operation(summary = "保存人工校正结果")
    @Parameter(name = "parsedDataId", description = "解析数据 ID", required = true)
    @PreAuthorize("@ss.hasPermission('jijian:import:update')")
    @ApiAccessLog(operateType = UPDATE)
    public CommonResult<Boolean> saveCorrection(
            @PathVariable("parsedDataId") Long parsedDataId,
            @Valid @RequestBody SaveCorrectionReqVO reqVO) {
        parsedDataService.saveCorrectedJson(parsedDataId, reqVO.getCorrectedJson());
        return success(true);
    }

    // ─── 确认写入（通用） ─────────────────────────────────────────────────

    @PostMapping("/{parsedDataId}/confirm")
    @Operation(summary = "确认写入正式业务表（通用，按 formType 自动路由）")
    @Parameter(name = "parsedDataId", description = "解析数据 ID", required = true)
    @PreAuthorize("@ss.hasPermission('jijian:import:confirm')")
    @ApiAccessLog(operateType = CREATE)
    public CommonResult<ConfirmWriteResultRespVO> confirmWrite(@PathVariable("parsedDataId") Long parsedDataId) {
        ConfirmWriteResult result = parsedDataService.confirmWrite(parsedDataId);
        ConfirmWriteResultRespVO respVO = new ConfirmWriteResultRespVO();
        respVO.setParsedDataId(parsedDataId);
        respVO.setFormType(result.getFormType());
        respVO.setBusinessTable(result.getBusinessTable());
        respVO.setConfirmedIds(result.getConfirmedIds());
        respVO.setConfirmedCount(result.getConfirmedCount());
        respVO.setIdempotent(result.isIdempotent());
        respVO.setTotalRows(result.getTotalRows());
        respVO.setSkippedRows(result.getSkippedRows());
        respVO.setFailedRows(result.getFailedRows());
        respVO.setSkippedMessages(result.getSkippedMessages());
        respVO.setFailedMessages(result.getFailedMessages());
        return success(respVO);
    }

    /**
     * 向后兼容：专用房产确认端点，返回 propertyId。
     * 新业务请统一使用 /confirm。
     */
    @PostMapping("/{parsedDataId}/confirm-property")
    @Operation(summary = "确认写入房产表（兼容旧版，推荐改用 /confirm）")
    @Parameter(name = "parsedDataId", description = "解析数据 ID", required = true)
    @PreAuthorize("@ss.hasPermission('jijian:property:create')")
    @ApiAccessLog(operateType = CREATE)
    public CommonResult<ConfirmPropertyRespVO> confirmProperty(@PathVariable("parsedDataId") Long parsedDataId) {
        Long propertyId = parsedDataService.confirmProperty(parsedDataId);
        ConfirmPropertyRespVO respVO = new ConfirmPropertyRespVO();
        respVO.setParsedDataId(parsedDataId);
        respVO.setPropertyId(propertyId);
        return success(respVO);
    }

    // ─── 删除导入批次业务数据 ────────────────────────────────────────────────

    @DeleteMapping("/{parsedDataId}/business-data")
    @Operation(summary = "删除某次导入批次写入的业务数据（仅删 source_parsed_data_id 匹配的记录）")
    @Parameter(name = "parsedDataId", description = "解析数据 ID", required = true)
    @PreAuthorize("@ss.hasPermission('jijian:import:delete')")
    @ApiAccessLog(operateType = UPDATE)
    public CommonResult<DeleteBusinessDataRespVO> deleteBusinessData(
            @PathVariable("parsedDataId") Long parsedDataId) {
        DeleteBusinessDataResult result = parsedDataService.deleteBusinessDataByParsedDataId(parsedDataId);
        DeleteBusinessDataRespVO respVO = new DeleteBusinessDataRespVO();
        respVO.setParsedDataId(result.parsedDataId);
        respVO.setBusinessTable(result.businessTable);
        respVO.setDeletedRows(result.deletedRows);
        respVO.setMessage(result.message);
        return success(respVO);
    }

    // ─── 内部转换 ─────────────────────────────────────────────────────────

    private ParsedDataRespVO convert(ParsedDataDO parsedData) {
        if (parsedData == null) return null;
        ImportRecordDO importRecord = importRecordService.getImportRecord(parsedData.getImportRecordId());
        ParsedDataRespVO respVO = new ParsedDataRespVO();
        respVO.setId(parsedData.getId());
        respVO.setImportRecordId(parsedData.getImportRecordId());
        respVO.setFormType(parsedData.getFormType());
        respVO.setRawText(parsedData.getRawText());
        respVO.setParsedJson(parsedData.getParsedJson());
        respVO.setCorrectedJson(parsedData.getCorrectedJson());
        respVO.setConfidence(parsedData.getConfidence());
        respVO.setStatus(parsedData.getStatus());
        respVO.setConfirmStatus(parsedData.getConfirmStatus());
        respVO.setErrorMsg(parsedData.getErrorMsg());
        respVO.setConfirmedPropertyId(parsedData.getConfirmedPropertyId());
        // 追溯字段
        respVO.setBusinessTable(parsedData.getBusinessTable());
        respVO.setBusinessIds(parsedData.getBusinessIds());
        respVO.setConfirmTime(formatDateTime(parsedData.getConfirmTime()));
        respVO.setCreatedAt(formatDateTime(parsedData.getCreateTime()));
        if (importRecord != null) {
            respVO.setFileName(importRecord.getFileName());
            respVO.setSourceType(importRecord.getSourceType());
            respVO.setDetectedFormType(importRecord.getDetectedFormType());
        }
        return respVO;
    }

    private String formatDateTime(LocalDateTime dt) {
        return dt == null ? null : DATE_TIME_FORMATTER.format(dt);
    }

}
