package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.importrecord.ImportRecordMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.parseddata.ParsedDataMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.framework.JijianProperties;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteHandlerRegistry;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import cn.iocoder.yudao.module.jijian.service.ocr.OcrResult;
import cn.iocoder.yudao.module.jijian.service.ocr.OcrService;
import cn.iocoder.yudao.module.jijian.util.JijianExcelMergedCellUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Slf4j
@Service
@Validated
public class ParsedDataServiceImpl implements ParsedDataService {

    private static final String STATUS_SUCCESS          = "success";
    private static final String STATUS_FAILED           = "failed";
    // confirm_status 枚举值（不再复用解析 status 字段）
    private static final String CONFIRM_STATUS_PENDING   = "pending";
    private static final String CONFIRM_STATUS_CONFIRMED = "confirmed";

    /** rawText 中首行示例展示字段数 */
    private static final int DETECT_SAMPLE_COLS = 3;
    private static final int OCR_HEADER_SCAN_LIMIT = 10;
    private static final int OCR_ALL_PDF_PAGES = 0;
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;
    private static final Set<String> ALLOWED_UPLOAD_EXTENSIONS = new HashSet<>(
            Arrays.asList("xls", "xlsx", "csv", "jpg", "jpeg", "png", "pdf"));
    private static final List<List<String>> OCR_HEADER_ALIASES = Arrays.asList(
            Arrays.asList("部门", "所在部门", "科室"),
            Arrays.asList("申请人", "姓名", "人员", "员工"),
            Arrays.asList("休假地点", "地点", "目的地"),
            Arrays.asList("疗养假开始时间", "休假开始时间", "开始时间", "开始日期"),
            Arrays.asList("疗养假结束时间", "休假结束时间", "结束时间", "结束日期"),
            Arrays.asList("序号"),
            Arrays.asList("商品名称", "品名", "项目名称", "物品名称"),
            Arrays.asList("规格", "型号", "规格等级", "规格、等级"),
            Arrays.asList("单位"), Arrays.asList("数量"),
            Arrays.asList("单价", "价格"), Arrays.asList("小计", "金额"),
            Arrays.asList("备注"), Arrays.asList("房产地址", "地址"),
            Arrays.asList("房产名称"), Arrays.asList("产权信息", "产权"),
            Arrays.asList("面积"), Arrays.asList("租赁情况"));

    /** 允许按 source_parsed_data_id 删除数据的业务表白名单（只删导入批次数据，不碰其他表） */
    private static final Set<String> DELETABLE_BUSINESS_TABLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "jijian_leave_health",
            "jijian_leave_personal",
            "jijian_attendance_daily",
            "jijian_business_trip",
            "jijian_compensatory_leave",
            "jijian_property",
            "jijian_lessee",
            "jijian_lease_contract",
            "jijian_canteen_supplier",
            "jijian_canteen_market_price"
    )));

    @Resource private ParsedDataMapper            parsedDataMapper;
    @Resource private ImportRecordMapper          importRecordMapper;
    @Resource private ConfirmWriteHandlerRegistry handlerRegistry;
    @Resource private JijianProperties            jijianProperties;
    @Resource private OcrService                  ocrService;
    @Resource private JdbcTemplate                jdbcTemplate;
    @Resource private FileApi                     fileApi;
    @Resource private LeaseContractParseService   leaseContractParseService;
    @Resource private JijianFormTypeAutoDetectService formTypeAutoDetectService;

    // ==================== 上传解析入口 ====================

    @Override
    public ParsedDataDO parseAndCreate(ImportRecordDO importRecord, MultipartFile file) {
        try {
            validateUploadFile(file);
            String fname = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            ParsedPayload payload;
            String formType;

            if (isImageOrPdf(fname) && isLikelyLeaseContractFile(fname)) {
                payload = parseLeaseContract(file, fname);
                formType = FormTypeConstants.LEASE_CONTRACT;
            } else {
                payload = parseGenericPayload(file, fname);
                formType = detectFormType(payload, fname);
                if (shouldUseLeaseContractParser(fname, payload.detectText, importRecord, formType)) {
                    payload = parseLeaseContract(file, fname);
                    formType = FormTypeConstants.LEASE_CONTRACT;
                }
            }

            if (FormTypeConstants.ATTENDANCE.equals(formType)) {
                normalizeAttendanceHeaders(payload.parsedData);
            }
            if (FormTypeConstants.CANTEEN.equals(formType)) {
                enrichCanteenParsedData(payload.parsedData);
            }
            if (FormTypeConstants.CANTEEN_MARKET_PRICE.equals(formType)) {
                enrichCanteenMarketPriceParsedData(payload.parsedData);
            }
            if (StrUtil.isBlank(formType)) {
                String hint = extractHeadersHint(payload);
                return createFailedParsedData(importRecord,
                        "未能识别业务类型，请确认文件包含以下之一的关键词：房产/租赁/合同/考勤/打卡/疗休养/事假/出差/调休/食堂"
                        + (StrUtil.isNotBlank(hint) ? "（当前识别到的表头：" + hint + "）" : ""));
            }

            ParsedDataDO parsedData = ParsedDataDO.builder()
                    .importRecordId(importRecord.getId())
                    .formType(formType)
                    .rawText(StrUtil.maxLength(payload.rawText, 2000))
                    .parsedJson(JSONUtil.toJsonStr(payload.parsedData))
                    .confidence(new BigDecimal("0.86"))
                    .status(STATUS_SUCCESS)
                    .confirmStatus(CONFIRM_STATUS_PENDING)
                    .build();
            parsedDataMapper.insert(parsedData);
            updateImportRecord(importRecord, formType, STATUS_SUCCESS);
            return parsedData;

        } catch (Exception ex) {
            return createFailedParsedData(importRecord,
                    StrUtil.blankToDefault(ex.getMessage(), "文件解析失败，请检查文件格式"));
        }
    }

    @Override
    public ParsedDataDO parseAndCreateWithFormType(ImportRecordDO importRecord, MultipartFile file, String forcedFormType) {
        try {
            validateUploadFile(file);
            String fname = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            ParsedPayload payload = FormTypeConstants.LEASE_CONTRACT.equals(forcedFormType)
                    ? parseLeaseContract(file, fname)
                    : parseGenericPayload(file, fname);

            if (FormTypeConstants.ATTENDANCE.equals(forcedFormType)) {
                normalizeAttendanceHeaders(payload.parsedData);
            }
            if (FormTypeConstants.CANTEEN.equals(forcedFormType)) {
                enrichCanteenParsedData(payload.parsedData);
            }
            if (FormTypeConstants.CANTEEN_MARKET_PRICE.equals(forcedFormType)) {
                enrichCanteenMarketPriceParsedData(payload.parsedData);
            }
            ParsedDataDO parsedData = ParsedDataDO.builder()
                    .importRecordId(importRecord.getId())
                    .formType(forcedFormType)
                    .rawText(StrUtil.maxLength(payload.rawText, 2000))
                    .parsedJson(JSONUtil.toJsonStr(payload.parsedData))
                    .confidence(new BigDecimal("0.86"))
                    .status(STATUS_SUCCESS)
                    .confirmStatus(CONFIRM_STATUS_PENDING)
                    .build();
            parsedDataMapper.insert(parsedData);
            updateImportRecord(importRecord, forcedFormType, STATUS_SUCCESS);
            return parsedData;

        } catch (Exception ex) {
            return createFailedParsedData(importRecord,
                    StrUtil.blankToDefault(ex.getMessage(), "文件解析失败，请检查文件格式"));
        }
    }

    private ParsedPayload parseGenericPayload(MultipartFile file, String fname) throws Exception {
        if (isExcel(fname)) {
            return parseExcel(file);
        }
        if (isCsv(fname)) {
            return parseCsv(file);
        }
        if (isImageOrPdf(fname)) {
            return parseImageOrPdf(file, fname);
        }
        return parseTextFile(file);
    }

    private void validateUploadFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty() || file.getSize() <= 0 || file.getSize() > MAX_UPLOAD_BYTES) {
            throw exception(PARSED_DATA_FILE_INVALID);
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "");
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")
                || originalName.indexOf('\0') >= 0) {
            throw exception(PARSED_DATA_FILE_INVALID);
        }
        String extension = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!ALLOWED_UPLOAD_EXTENSIONS.contains(extension)) {
            throw exception(PARSED_DATA_FILE_FORMAT_UNSUPPORTED);
        }
        byte[] header = new byte[8];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        }
        if (!matchesFileSignature(extension, header, read)) {
            throw exception(PARSED_DATA_FILE_INVALID);
        }
    }

    private boolean matchesFileSignature(String extension, byte[] header, int read) {
        if (read <= 0) {
            return false;
        }
        if ("xlsx".equals(extension)) {
            return read >= 4 && header[0] == 0x50 && header[1] == 0x4b
                    && header[2] == 0x03 && header[3] == 0x04;
        }
        if ("xls".equals(extension)) {
            return read >= 8 && (header[0] & 0xff) == 0xd0 && (header[1] & 0xff) == 0xcf
                    && (header[2] & 0xff) == 0x11 && (header[3] & 0xff) == 0xe0;
        }
        if ("pdf".equals(extension)) {
            return read >= 4 && header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
        }
        if ("png".equals(extension)) {
            return read >= 8 && (header[0] & 0xff) == 0x89 && header[1] == 'P'
                    && header[2] == 'N' && header[3] == 'G';
        }
        if ("jpg".equals(extension) || "jpeg".equals(extension)) {
            return read >= 3 && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
        }
        if ("csv".equals(extension)) {
            for (int i = 0; i < read; i++) {
                if (header[i] == 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public ParsedDataDO createFailedParsedData(ImportRecordDO importRecord, String errorMsg) {
        ParsedDataDO parsedData = ParsedDataDO.builder()
                .importRecordId(importRecord.getId())
                .formType(importRecord.getDetectedFormType())
                .rawText("")
                .parsedJson("{}")
                .status(STATUS_FAILED)
                .errorMsg(StrUtil.maxLength(errorMsg, 500))
                .build();
        parsedDataMapper.insert(parsedData);
        updateImportRecord(importRecord, importRecord.getDetectedFormType(), STATUS_FAILED);
        return parsedData;
    }

    @Override
    public ParsedDataDO getParsedDataById(Long parsedDataId) {
        return parsedDataMapper.selectById(parsedDataId);
    }

    @Override
    public ParsedDataDO getLatestParsedData(Long importRecordId) {
        return parsedDataMapper.selectLatestByImportRecordId(importRecordId);
    }

    @Override
    public List<ParsedDataDO> getParsedDataList(Long importRecordId) {
        return parsedDataMapper.selectListByImportRecordId(importRecordId);
    }

    // ==================== 用户校正 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCorrectedJson(Long parsedDataId, String correctedJson) {
        ParsedDataDO parsedData = parsedDataMapper.selectById(parsedDataId);
        if (parsedData == null) throw exception(PARSED_DATA_NOT_EXISTS);
        if (CONFIRM_STATUS_CONFIRMED.equals(parsedData.getConfirmStatus())) throw exception(PARSED_DATA_ALREADY_CONFIRMED);
        ParsedDataDO update = new ParsedDataDO();
        update.setId(parsedDataId);
        update.setCorrectedJson(correctedJson);
        parsedDataMapper.updateById(update);
    }

    // ==================== 通用确认写入 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConfirmWriteResult confirmWrite(Long parsedDataId) {
        ParsedDataDO parsedData = parsedDataMapper.selectById(parsedDataId);
        if (parsedData == null) throw exception(PARSED_DATA_NOT_EXISTS);

        // 已确认：幂等返回，不重复写正式表
        if (CONFIRM_STATUS_CONFIRMED.equals(parsedData.getConfirmStatus())) {
            return buildIdempotentResult(parsedData, parsedDataId);
        }

        // 解析未成功则不允许确认
        if (!STATUS_SUCCESS.equals(parsedData.getStatus())) throw exception(PARSED_DATA_CANNOT_CONFIRM);

        String formType = parsedData.getFormType();
        ConfirmWriteHandler handler = handlerRegistry.getHandler(formType);
        if (handler == null) throw exception(PARSED_DATA_FORM_TYPE_NOT_SUPPORTED);

        // 原子 CAS：将 confirm_status 从 pending 置为 confirmed
        // 若 rows=0 说明并发请求已先行确认，走幂等路径
        int cas = parsedDataMapper.casConfirmStatus(parsedDataId, CONFIRM_STATUS_PENDING, CONFIRM_STATUS_CONFIRMED);
        if (cas == 0) {
            ParsedDataDO refreshed = parsedDataMapper.selectById(parsedDataId);
            return buildIdempotentResult(refreshed != null ? refreshed : parsedData, parsedDataId);
        }

        // CAS 成功后才执行正式表写入（在同一事务内；若写入失败，事务回滚会撤销 CAS）
        ConfirmWriteResult result;
        try {
            result = handler.doConfirm(parsedData);
        } catch (ServiceException se) {
            // ServiceException 直接上抛，前端可见具体消息
            log.error("[confirmWrite] parsedDataId={} formType={} 业务写入失败: {}", parsedDataId, formType, se.getMessage());
            throw se;
        } catch (Exception e) {
            // 非业务异常（DB 错误、NPE 等）转换为可读消息后上抛
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[confirmWrite] parsedDataId={} formType={} 写入异常", parsedDataId, formType, e);
            throw new ServiceException(CONFIRM_WRITE_DB_ERROR.getCode(),
                    "写入失败（" + formType + "）：" + detail);
        }

        // 回写追溯字段（confirm_status 已在 CAS 中更新，此处补其余字段）
        Long userId = null;
        try { userId = SecurityFrameworkUtils.getLoginUserId(); } catch (Exception ignore) {}

        ParsedDataDO update = new ParsedDataDO();
        update.setId(parsedDataId);
        update.setConfirmTime(LocalDateTime.now());
        update.setConfirmUserId(userId);
        update.setBusinessTable(result.getBusinessTable() != null
                ? result.getBusinessTable() : handler.getBusinessTableName());
        // 超过 200 条时只存前 200 个 ID（business_ids 是追溯字段，不需要全量）
        List<Long> idsToStore = result.getConfirmedIds().size() > 200
                ? result.getConfirmedIds().subList(0, 200)
                : result.getConfirmedIds();
        update.setBusinessIds(JSONUtil.toJsonStr(idsToStore));
        if (FormTypeConstants.PROPERTY.equals(formType) && !result.getConfirmedIds().isEmpty()) {
            update.setConfirmedPropertyId(result.getConfirmedIds().get(0));
        }
        parsedDataMapper.updateById(update);
        updateImportRecordStatus(parsedData.getImportRecordId(), formType, CONFIRM_STATUS_CONFIRMED);
        return result;
    }

    private ConfirmWriteResult buildIdempotentResult(ParsedDataDO parsedData, Long parsedDataId) {
        String existingBusinessTable = parsedData.getBusinessTable();
        List<Long> existingIds = new ArrayList<>();

        // 始终从 DB 查询实际 confirmedCount，不依赖 business_ids（business_ids 截断为 200 条会导致 count 偏小）
        ConfirmWriteHandler handler = handlerRegistry.getHandler(parsedData.getFormType());
        if (handler != null) {
            existingIds = handler.queryConfirmedSummary(parsedDataId).stream()
                    .map(m -> parseLong(m.get("记录ID")))
                    .filter(id -> id != null)
                    .collect(Collectors.toList());
            if (StrUtil.isBlank(existingBusinessTable)) existingBusinessTable = handler.getBusinessTableName();
        } else if (StrUtil.isNotBlank(parsedData.getBusinessIds())) {
            // handler 不存在时降级使用存储的 IDs（最多 200 条，仅兜底）
            try {
                cn.hutool.json.JSONArray arr = cn.hutool.json.JSONUtil.parseArray(parsedData.getBusinessIds());
                for (int i = 0; i < arr.size(); i++) existingIds.add(arr.getLong(i));
            } catch (Exception ignore) {}
        }
        return ConfirmWriteResult.idempotent(parsedData.getFormType(), existingBusinessTable, existingIds);
    }

    @Override
    public Long confirmProperty(Long parsedDataId) {
        ConfirmWriteResult result = confirmWrite(parsedDataId);
        return result.getConfirmedIds().isEmpty() ? null : result.getConfirmedIds().get(0);
    }

    // ==================== 删除导入批次业务数据 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteBusinessDataResult deleteBusinessData(Long importRecordId) {
        // 通过 importRecordId 找到最近一次已确认的 parsedData
        ParsedDataDO parsedData = parsedDataMapper.selectLatestByImportRecordId(importRecordId);
        if (parsedData == null) {
            throw new ServiceException(PARSED_DATA_NOT_EXISTS.getCode(), "未找到该导入记录对应的解析数据");
        }
        return deleteBusinessDataByParsedDataId(parsedData.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeleteBusinessDataResult deleteBusinessDataByParsedDataId(Long parsedDataId) {
        ParsedDataDO parsedData = parsedDataMapper.selectById(parsedDataId);
        if (parsedData == null) {
            throw new ServiceException(PARSED_DATA_NOT_EXISTS.getCode(), "解析数据不存在：" + parsedDataId);
        }

        String businessTable = parsedData.getBusinessTable();
        if (StrUtil.isBlank(businessTable)) {
            // 尚未确认写入，或业务表字段未回写：从 formType 推断
            businessTable = resolveBusinessTableFromFormType(parsedData.getFormType());
        }
        if (StrUtil.isBlank(businessTable)) {
            int metadataDeleted = deleteImportMetadata(parsedData);
            return new DeleteBusinessDataResult(0, null, parsedDataId,
                    "该解析记录尚未确认写入业务表；已删除导入记录和解析记录 " + metadataDeleted + " 条");
        }

        // 白名单校验，防止误操作非业务表
        if (!DELETABLE_BUSINESS_TABLES.contains(businessTable)) {
            throw new ServiceException(-1, "不允许删除的表：" + businessTable);
        }

        // COUNT 再 DELETE，严格限定 source_parsed_data_id
        String countSql = "SELECT COUNT(*) FROM `" + businessTable + "` WHERE source_parsed_data_id = ? AND deleted = 0";
        Integer count = jdbcTemplate.queryForObject(countSql, Integer.class, parsedDataId);
        int toDelete = count != null ? count : 0;

        int deleted = 0;
        if (toDelete > 0) {
            String deleteSql = "UPDATE `" + businessTable + "` SET deleted = 1, updater = 'batch-delete', update_time = NOW()" +
                               " WHERE source_parsed_data_id = ? AND deleted = 0";
            deleted = jdbcTemplate.update(deleteSql, parsedDataId);

            log.info("[deleteBusinessData] parsedDataId={} businessTable={} 逻辑删除 {} 条",
                    parsedDataId, businessTable, deleted);
        }
        int metadataDeleted = deleteImportMetadata(parsedData);

        return new DeleteBusinessDataResult(deleted, businessTable, parsedDataId,
                "已删除业务数据 " + deleted + " 条，并删除导入记录/解析记录 " + metadataDeleted
                        + " 条（表：" + businessTable + "，批次ID：" + parsedDataId + "）");
    }

    private int deleteImportMetadata(ParsedDataDO parsedData) {
        int deleted = 0;
        Long importRecordId = parsedData.getImportRecordId();
        if (importRecordId != null) {
            deleted += parsedDataMapper.delete(new LambdaQueryWrapper<ParsedDataDO>()
                    .eq(ParsedDataDO::getImportRecordId, importRecordId));
            deleted += importRecordMapper.deleteById(importRecordId);
        } else {
            deleted += parsedDataMapper.deleteById(parsedData.getId());
        }
        return deleted;
    }

    /** 根据 formType 推断对应的业务表名 */
    private String resolveBusinessTableFromFormType(String formType) {
        if (StrUtil.isBlank(formType)) return null;
        ConfirmWriteHandler handler = handlerRegistry.getHandler(formType);
        return handler != null ? handler.getBusinessTableName() : null;
    }

    // ==================== Excel 解析 ====================

    private ParsedPayload parseExcel(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            int sheetCount = workbook.getNumberOfSheets();
            Sheet sheet = sheetCount > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("Excel 文件没有可读取的 Sheet");

            if (sheetCount > 1) {
                log.info("[parseExcel] 文件 {} 共 {} 个 Sheet，仅导入第 1 个：「{}」",
                        fileName, sheetCount, sheet.getSheetName());
            }

            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            // 先展开合并单元格，再解析表结构（适用于所有 9 种业务表单）
            List<List<String>> rawRows = JijianExcelMergedCellUtils.readSheetRowsWithMerge(sheet, formatter, evaluator);
            // 保留全量单元格文本（每行用\n分隔），供食堂解析提取日期/供应商
            String fullScanText = rawRows.stream()
                    .map(row -> row.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining(" ")))
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.joining("\n"));
            TableStructureResolver.Result resolved = TableStructureResolver.resolve(rawRows);
            List<String> headers = resolved.getHeaders();
            List<Map<String, String>> rows = resolved.getRows();

            int totalRows = rows.size();
            Map<String, Object> parsed = buildParsedMap(fileName, sheet.getSheetName(), headers, rows, totalRows);
            parsed.put("sheetCount", sheetCount);   // 供前端判断是否有多个 sheet
            parsed.put("_fullScanText", fullScanText);
            String rawText   = buildRawText(fileName, sheet.getSheetName(), headers, rows, totalRows);
            String detectText = fileName + " " + sheet.getSheetName() + " "
                    + headers.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining(" "));
            return new ParsedPayload(rawText, detectText, parsed);
        }
    }

    // ==================== CSV 解析 ====================

    private ParsedPayload parseCsv(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        String content  = decodeBytes(file.getBytes());

        List<String> headers = new ArrayList<>();
        List<Map<String, String>> rows = new ArrayList<>();

        try {
            CSVParser parser = CSVParser.parse(new StringReader(content),
                    CSVFormat.DEFAULT.withFirstRecordAsHeader().withIgnoreEmptyLines().withTrim());
            headers.addAll(parser.getHeaderNames());
            for (CSVRecord rec : parser) {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (String h : headers) {
                    try { rowMap.put(h, rec.get(h)); } catch (Exception e) { rowMap.put(h, ""); }
                }
                if (rowMap.values().stream().allMatch(StrUtil::isBlank)) continue;
                rows.add(rowMap);
            }
        } catch (Exception e) {
            parseTsvFallback(content, headers, rows);
        }

        if (headers.isEmpty()) {
            throw new IllegalArgumentException("CSV 文件未能识别到表头，请确认编码为 UTF-8/GBK，分隔符为逗号或制表符");
        }

        int totalRows = rows.size();
        Map<String, Object> parsed = buildParsedMap(fileName, "CSV", headers, rows, totalRows);
        String rawText    = buildRawText(fileName, "CSV", headers, rows, totalRows);
        String detectText = fileName + " " + String.join(" ", headers);
        return new ParsedPayload(rawText, detectText, parsed);
    }

    private void parseTsvFallback(String content, List<String> headers, List<Map<String, String>> rows) {
        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) return;
        String delim = lines[0].contains("\t") ? "\t" : ",";
        String[] hdrs = lines[0].split(delim, -1);
        for (String h : hdrs) headers.add(StrUtil.trim(h));
        for (int i = 1; i < lines.length; i++) {
            String[] vals = lines[i].split(delim, -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                row.put(headers.get(c), c < vals.length ? StrUtil.trim(vals[c]) : "");
            }
            if (row.values().stream().allMatch(StrUtil::isBlank)) continue;
            rows.add(row);
        }
    }

    // ==================== 图片 / PDF → OCR ====================

    private ParsedPayload parseImageOrPdf(MultipartFile file, String fname) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");

        if (!jijianProperties.getOcr().isEnabled()) {
            throw exception(OCR_SERVICE_NOT_ENABLED);
        }

        byte[] fileBytes = file.getBytes();
        OcrResult ocrResult = ocrService.recognize(fileBytes, fileName);

        if (!ocrResult.isSuccess()) {
            throw exception(OCR_RECOGNITION_FAILED);
        }

        return buildOcrPayload(fileName, ocrResult);
    }

    /**
     * 将 OCR 结果转换为统一的 ParsedPayload。
     * 优先使用 Python 服务已解析的 tables；回退到 lines 自动分列。
     */
    private ParsedPayload buildOcrPayload(String fileName, OcrResult ocrResult) {
        // ── 优先：使用 Python 服务解析好的表格 ────────────────────────────
        if (ocrResult.getTables() != null && !ocrResult.getTables().isEmpty()) {
            for (Map<String, Object> tbl : ocrResult.getTables()) {
                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) tbl.get("headers");
                @SuppressWarnings("unchecked")
                List<List<String>> rawRows = (List<List<String>>) tbl.get("rows");
                @SuppressWarnings("unchecked")
                List<List<String>> fallbackRows = (List<List<String>>) tbl.get("rawRows");
                List<List<String>> sourceRows = new ArrayList<>();
                if (headers != null) sourceRows.add(headers);
                if (rawRows != null) sourceRows.addAll(rawRows);
                try {
                    TableStructureResolver.Result resolved = TableStructureResolver.resolve(sourceRows);
                    return buildPayloadFromHeadersAndRows(fileName,
                            resolved.getHeaders(), resolved.getRows(), ocrResult);
                } catch (IllegalArgumentException ignore) {
                    if (fallbackRows != null && !fallbackRows.isEmpty()) {
                        try {
                            TableStructureResolver.Result resolved = TableStructureResolver.resolve(fallbackRows);
                            return buildPayloadFromHeadersAndRows(fileName,
                                    resolved.getHeaders(), resolved.getRows(), ocrResult);
                        } catch (IllegalArgumentException ignored) {
                            // Try the line-based fallback below.
                        }
                    }
                }
            }
        }

        // ── 回退：从 lines 自动分列 ───────────────────────────────────────
        if (ocrResult.getLines() != null && !ocrResult.getLines().isEmpty()) {
            ParsedPayload fromLines = tryParseFromLines(fileName, ocrResult);
            if (fromLines != null) return fromLines;
        }

        throw exception(OCR_NO_TABLE_DETECTED);
    }

    /** 从 lines 尝试多种分隔符解析表格 */
    private ParsedPayload tryParseFromLines(String fileName, OcrResult ocrResult) {
        List<String> lines = ocrResult.getLines();
        String[] delimiters = {"\t", "\\s{2,}", "，", ",", "｜", "\\|"};

        List<List<String>> candidateRows = new ArrayList<>();

        for (String line : lines) {
            List<String> parts = splitLineMultiDelim(line, delimiters);
            if (parts.size() >= 2) candidateRows.add(parts);
        }

        int headerIndex = findTrustedOcrHeaderIndex(candidateRows);
        if (headerIndex < 0) return null;
        List<String> headers = candidateRows.get(headerIndex);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int rowIndex = headerIndex + 1; rowIndex < candidateRows.size(); rowIndex++) {
            List<String> parts = candidateRows.get(rowIndex);
            if (shouldSkipOcrDataRow(parts)) continue;
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                row.put(headers.get(i), i < parts.size() ? parts.get(i) : "");
            }
            if (row.values().stream().filter(StrUtil::isNotBlank).count() >= 2) rows.add(row);
        }

        if (rows.isEmpty()) {
            // 有表头但无数据行 → 允许进入预览让用户手工补录
            Map<String, Object> parsed = buildParsedMap(fileName, "OCR", headers, rows, 0);
            parsed.put("ocrNotice", "已识别到表头，但未识别到有效数据行，请检查图片清晰度或手工补录");
            String rawText    = buildRawText(fileName, "OCR", headers, rows, 0);
            String detectText = fileName + " " + String.join(" ", headers) + " " + ocrResult.getFullText();
            return new ParsedPayload(rawText, detectText, parsed);
        }

        return buildPayloadFromHeadersAndRows(fileName, headers, rows, ocrResult);
    }

    private ParsedPayload buildPayloadFromHeadersAndRows(String fileName, List<String> headers,
            List<Map<String, String>> rows, OcrResult ocrResult) {
        int totalRows = rows.size();
        Map<String, Object> parsed = buildParsedMap(fileName, "OCR", headers, rows, totalRows);
        if (StrUtil.isNotBlank(ocrResult.getNotice())) {
            parsed.put("ocrNotice", ocrResult.getNotice());
        }
        parsed.put("ocrText", StrUtil.maxLength(ocrResult.getFullText(), 1000));
        parsed.put("_fullScanText", ocrResult.getFullText());  // 完整 OCR 文本，供食堂日期/供应商提取

        String rawText    = buildRawText(fileName, "OCR", headers, rows, totalRows);
        String detectText = fileName + " " + String.join(" ", headers) + " " + ocrResult.getFullText();
        return new ParsedPayload(rawText, detectText, parsed);
    }

    private List<String> splitLineMultiDelim(String line, String[] regexDelimiters) {
        for (String delim : regexDelimiters) {
            String[] parts = line.split(delim, -1);
            if (parts.length >= 2) {
                List<String> result = Arrays.stream(parts)
                        .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                if (result.size() >= 2) return result;
            }
        }
        return new ArrayList<>();
    }

    private int findTrustedOcrHeaderIndex(List<List<String>> rows) {
        int limit = Math.min(rows.size(), OCR_HEADER_SCAN_LIMIT);
        int bestIndex = -1;
        int bestScore = 0;
        for (int i = 0; i < limit; i++) {
            int score = countOcrHeaderHits(rows.get(i));
            if (score >= 2 && score > bestScore) {
                bestIndex = i;
                bestScore = score;
            }
        }
        return bestIndex;
    }

    private boolean isTrustedOcrHeader(List<String> headers) {
        return headers != null && countOcrHeaderHits(headers) >= 2;
    }

    private int countOcrHeaderHits(List<String> cells) {
        int hits = 0;
        for (List<String> aliases : OCR_HEADER_ALIASES) {
            boolean matched = false;
            for (String cell : cells) {
                String normalizedCell = normalizeOcrCell(cell);
                for (String alias : aliases) {
                    String normalizedAlias = normalizeOcrCell(alias);
                    if (normalizedCell.equals(normalizedAlias) || normalizedCell.contains(normalizedAlias)) {
                        matched = true;
                        break;
                    }
                }
                if (matched) break;
            }
            if (matched) hits++;
        }
        return hits;
    }

    private boolean shouldSkipOcrDataRow(List<String> row) {
        if (row == null || row.stream().allMatch(StrUtil::isBlank)) return true;
        String joined = row.stream().filter(StrUtil::isNotBlank).collect(Collectors.joining());
        return containsAny(joined, "小计", "合计", "总计");
    }

    private String normalizeOcrCell(String value) {
        return StrUtil.blankToDefault(value, "").replaceAll("[\\s，,、]+", "");
    }

    // ==================== 纯文本解析 ====================

    private ParsedPayload parseTextFile(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        byte[] bytes = file.getBytes();
        int len = Math.min(bytes.length, 8192);
        String content = decodeBytes(java.util.Arrays.copyOf(bytes, len));

        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("fileName", fileName);
        parsed.put("source", "text");
        parsed.put("textPreview", StrUtil.maxLength(content, 2000));

        String rawText    = "文件：" + fileName + " | 文本内容（前2000字）：" + StrUtil.maxLength(content, 500);
        String detectText = fileName + " " + content;
        return new ParsedPayload(rawText, detectText, parsed);
    }

    // ==================== 食堂供应专用：日期/供应商注入 ====================

    // 带前缀的日期：日期：2025年4月3号
    private static final Pattern CANTEEN_DATE_PATTERN = Pattern.compile(
            "(?:日期|时间)[：:﹕]\\s*(\\d{4})[年\\-/](\\d{1,2})[月\\-/](\\d{1,2})[号日]?");
    // 仅年月日（无前缀，兜底：OCR 可能丢失"日期："前缀）
    private static final Pattern CANTEEN_DATE_FALLBACK = Pattern.compile(
            "(?<![\\d\\u4e00-\\u9fa5])(\\d{4})[年](\\d{1,2})[月](\\d{1,2})[号日]?(?![\\d])");
    private static final Pattern CANTEEN_SUPPLIER_PATTERN = Pattern.compile(
            "供[应货]商[：:﹕]\\s*([^\\n\\r、,，;；]{1,60}?)(?=[\\s]*(?:客户|签字|签名|电话|联系|$)|[\\n\\r])");
    private static final Pattern CANTEEN_SUPPLIER_UNIT_PATTERN = Pattern.compile(
            "(?:配送单位|供应单位)[：:﹕]\\s*([^\\n\\r、,，;；]{1,60}?)(?=[\\s]*(?:客户|签字|签名|$)|[\\n\\r])");
    /** 供应商标签（冒号可缺失；同一行可能因合并单元格展开而重复多次） */
    private static final Pattern SUPPLIER_LABEL = Pattern.compile(
            "(?:供应商|供货商|供货单位|配送单位|供应单位)[：:﹕]?");
    /** 供应商行尾部噪音：噪音标签及其之后的内容全部丢弃 */
    private static final Pattern SUPPLIER_NOISE = Pattern.compile(
            "(?:客户签字|客户|签字|签名|盖章|日期|电话|联系人|联系方式)[：:﹕]?.*$");

    /** 从扫描文本中提取配送日期，格式化为 yyyy-MM-dd */
    private String extractDeliveryDate(String text) {
        if (StrUtil.isBlank(text)) return null;
        // 优先匹配有"日期："前缀的格式
        Matcher m = CANTEEN_DATE_PATTERN.matcher(text);
        if (m.find()) {
            return formatDate(m.group(1), m.group(2), m.group(3));
        }
        // 兜底：无前缀的年月日（OCR 可能丢失"日期："）
        Matcher m2 = CANTEEN_DATE_FALLBACK.matcher(text);
        if (m2.find()) {
            return formatDate(m2.group(1), m2.group(2), m2.group(3));
        }
        return null;
    }

    private String formatDate(String year, String month, String day) {
        return String.format("%d-%02d-%02d",
                Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }

    private static final Pattern SUPPLIER_TRAILING = Pattern.compile(
            "\\s+(客户|签字|签名|电话|联系方式|地址|盖章).*$");

    /**
     * 从扫描文本中提取供应商名称。
     *
     * <p>优先按行提取：找到含"供应商/供货商/供货单位/配送单位/供应单位"标签的行，
     * 剥离所有标签和尾部噪音（客户签字等），再对剩余 token 去重拼接。
     * 之所以要去重：Excel 落款行常用合并单元格（如 A:B="供应商："、C:H=公司名），
     * 合并展开后同一行文本变成"供应商： 供应商： 公司名 ×6"，旧正则因 60 字上限直接失配。
     */
    String extractSupplierName(String text) {
        if (StrUtil.isBlank(text)) return null;
        // 1) 按行提取（Excel 全文为 \n 分行；OCR 全文可能是无换行整段，超长行跳过交给兜底正则）
        for (String line : text.split("\\r?\\n")) {
            if (line.length() > 200 || !SUPPLIER_LABEL.matcher(line).find()) continue;
            String candidate = SUPPLIER_LABEL.matcher(line).replaceAll(" ");
            candidate = SUPPLIER_NOISE.matcher(candidate).replaceFirst(" ");
            candidate = dedupJoinTokens(candidate);
            if (isReasonableSupplierName(candidate)) return candidate;
        }
        // 2) 兜底：旧正则（处理标签与名称同段且无换行的 OCR 整段文本）
        Matcher m = CANTEEN_SUPPLIER_PATTERN.matcher(text);
        if (m.find()) {
            String candidate = cleanSupplier(m.group(1));
            if (isReasonableSupplierName(candidate)) return candidate;
        }
        Matcher m2 = CANTEEN_SUPPLIER_UNIT_PATTERN.matcher(text);
        if (m2.find()) {
            String candidate = cleanSupplier(m2.group(1));
            if (isReasonableSupplierName(candidate)) return candidate;
        }
        return null;
    }

    /** 供应商候选合理性校验：长度 2~60，且不含标题/汇总/签字类词（防止把整段文本误当名称） */
    private boolean isReasonableSupplierName(String candidate) {
        if (StrUtil.isBlank(candidate)) return false;
        if (candidate.length() < 2 || candidate.length() > 60) return false;
        return !containsAny(candidate, "配送单", "签字", "签名", "小计", "总计", "客户");
    }

    /**
     * 按空白拆分 token，去除相邻重复后拼接（不留空格，适配中文名称）。
     * 既消除合并单元格展开的重复，也能把 OCR 拆成多段的名称重新拼起来。
     */
    private String dedupJoinTokens(String s) {
        if (StrUtil.isBlank(s)) return "";
        StringBuilder sb = new StringBuilder();
        String prev = null;
        for (String token : s.trim().split("\\s+")) {
            if (token.isEmpty() || token.equals(prev)) continue;
            sb.append(token);
            prev = token;
        }
        return sb.toString().trim();
    }

    private String cleanSupplier(String raw) {
        if (raw == null) return null;
        // 去除尾部已知标签（客户签字/签名等）
        raw = SUPPLIER_TRAILING.matcher(raw.trim()).replaceFirst("").trim();
        return raw.isEmpty() ? null : raw;
    }

    /**
     * 食堂供应后处理：从 _fullScanText 提取日期/供应商，注入每个产品行。
     * 注入后删除临时键，不影响正式 parsedJson 结构。
     */
    @SuppressWarnings("unchecked")
    private void enrichCanteenParsedData(Map<String, Object> parsedData) {
        String scanText = (String) parsedData.remove("_fullScanText");
        if (StrUtil.isBlank(scanText)) {
            // OCR 路径：ocrText 作为备份（已截断，但通常足够）
            scanText = (String) parsedData.getOrDefault("ocrText", "");
        }
        if (StrUtil.isBlank(scanText)) return;

        String deliveryDate  = extractDeliveryDate(scanText);
        String supplierName  = extractSupplierName(scanText);
        Object headersObj = parsedData.get("headers");
        Object rowsObj    = parsedData.get("rows");
        if (!(headersObj instanceof List) || !(rowsObj instanceof List)) return;

        List<String>           headers = (List<String>) headersObj;
        List<Map<String, String>> rows = (List<Map<String, String>>) rowsObj;

        // 始终注入"时间"和"供应商"列，值为空时显示空格，用户可在预览中手工补齐
        if (!headers.contains("时间"))   headers.add("时间");
        if (!headers.contains("供应商")) headers.add("供应商");

        String dateVal     = StrUtil.blankToDefault(deliveryDate, "");
        String supplierVal = StrUtil.blankToDefault(supplierName, "");
        for (Map<String, String> row : rows) {
            row.put("时间",   dateVal);
            row.put("供应商", supplierVal);
        }
        parsedData.put("totalRows", rows.size());

        // 保留全文扫描文本（截断），供排查与后续元数据再提取（落款行不入 rows 但不丢全文）
        parsedData.put("scanText", StrUtil.maxLength(scanText, 4000));

        // 未能识别日期/供应商时，写入非阻断提示（追加到已有 ocrNotice 之后），供前端展示
        StringBuilder notice = new StringBuilder(
                StrUtil.blankToDefault((String) parsedData.get("ocrNotice"), ""));
        if (StrUtil.isBlank(deliveryDate)) {
            if (notice.length() > 0) notice.append(" ");
            notice.append("未识别到配送日期，请在预览中核对「时间」列并手工补齐后再确认写入。");
        }
        if (StrUtil.isBlank(supplierName)) {
            if (notice.length() > 0) notice.append(" ");
            notice.append("未识别到供应商，请在预览中核对「供应商」列并手工补齐后再确认写入。");
        }
        if (notice.length() > 0) {
            parsedData.put("ocrNotice", notice.toString());
        }
    }

    @SuppressWarnings("unchecked")
    private void enrichCanteenMarketPriceParsedData(Map<String, Object> parsedData) {
        String scanText = (String) parsedData.remove("_fullScanText");
        if (StrUtil.isBlank(scanText)) {
            scanText = (String) parsedData.getOrDefault("ocrText", "");
        }
        String title = extractMarketPriceTitle(scanText);
        String priceMonth = extractMarketPriceMonth(StrUtil.blankToDefault(title, scanText));

        Object headersObj = parsedData.get("headers");
        Object rowsObj = parsedData.get("rows");
        if (headersObj instanceof List && rowsObj instanceof List) {
            List<String> headers = (List<String>) headersObj;
            List<Map<String, String>> rows = (List<Map<String, String>>) rowsObj;
            if (!headers.contains("日期")) {
                headers.add("日期");
            }
            for (Map<String, String> row : rows) {
                row.put("日期", StrUtil.blankToDefault(priceMonth, ""));
            }
            parsedData.put("totalRows", rows.size());
        }
        parsedData.put("sourceTitle", StrUtil.blankToDefault(title, "义乌市民生商品市场零售价格信息公告"));
        parsedData.put("priceMonth", StrUtil.blankToDefault(priceMonth, ""));
        parsedData.put("scanText", StrUtil.maxLength(scanText, 4000));
        if (StrUtil.isBlank(priceMonth)) {
            parsedData.put("ocrNotice", "未能从标题识别公告年月，请在预览中核对「日期」列并手工补齐后再确认写入。");
        }
    }

    private String extractMarketPriceTitle(String text) {
        if (StrUtil.isBlank(text)) return null;
        for (String line : text.split("\\r?\\n")) {
            if (line.contains("民生商品市场零售价格") || line.contains("价格信息公告")) {
                return StrUtil.trim(line);
            }
        }
        Matcher matcher = Pattern.compile("义乌市民生商品市场零售价格信息公告[（(]?\\s*20\\d{2}\\s*年\\s*\\d{1,2}\\s*月[）)]?")
                .matcher(text);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractMarketPriceMonth(String text) {
        if (StrUtil.isBlank(text)) return null;
        Matcher cn = Pattern.compile("(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月").matcher(text);
        if (cn.find()) {
            return cn.group(1) + "-" + String.format("%02d", Integer.parseInt(cn.group(2)));
        }
        Matcher dash = Pattern.compile("(20\\d{2})[-/.](\\d{1,2})").matcher(text);
        if (dash.find()) {
            return dash.group(1) + "-" + String.format("%02d", Integer.parseInt(dash.group(2)));
        }
        return null;
    }

    // ==================== 租赁合同 OCR / 文本解析 ====================

    private ParsedPayload parseLeaseContract(MultipartFile file, String fname) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        byte[] fileBytes = file.getBytes();
        String contractText;

        if (isImageOrPdf(fname)) {
            if (!jijianProperties.getOcr().isEnabled()) {
                throw exception(OCR_SERVICE_NOT_ENABLED);
            }
            OcrResult ocrResult = ocrService.recognize(fileBytes, fileName, OCR_ALL_PDF_PAGES);
            if (!ocrResult.isSuccess()) {
                throw exception(OCR_RECOGNITION_FAILED);
            }
            contractText = ocrResult.getFullText();
        } else if (isExcel(fname)) {
            ParsedPayload excelPayload = parseExcel(file);
            contractText = StrUtil.blankToDefault((String) excelPayload.parsedData.get("_fullScanText"),
                    excelPayload.detectText);
        } else {
            contractText = decodeBytes(fileBytes);
        }

        String originalFileUrl;
        try {
            originalFileUrl = fileApi.createFile(fileBytes, fileName,
                    "jijian/lease-contract", file.getContentType());
        } catch (Exception ex) {
            log.warn("[parseLeaseContract] archive original file failed, fallback to logical path. fileName={}, error={}",
                    fileName, ex.getMessage());
            originalFileUrl = "jijian/lease-contract/" + fileName;
        }
        LeaseContractParseService.ParseResult result = leaseContractParseService.parse(
                fileName, contractText, originalFileUrl, originalFileUrl);

        List<Map<String, String>> rows = Collections.singletonList(result.getRow());
        Map<String, Object> parsed = buildParsedMap(fileName, "租赁合同识别",
                result.getHeaders(), rows, rows.size());
        parsed.put("formType", FormTypeConstants.LEASE_CONTRACT);
        parsed.put("ocrText", StrUtil.maxLength(contractText, 1000));
        parsed.put("scanText", StrUtil.maxLength(contractText, 4000));
        parsed.put("originalFileName", fileName);
        parsed.put("originalFileUrl", originalFileUrl);
        parsed.put("originalFilePath", originalFileUrl);
        parsed.put("parseStatus", result.getParseStatus());
        parsed.put("parseErrorMsg", result.getParseErrorMsg());
        parsed.put("rentInfoJson", result.getRow().get("租金明细JSON"));

        String rawText = "文件：" + fileName + " | 来源：租赁合同识别 | 表头："
                + String.join("，", result.getHeaders())
                + " | OCR原文：" + StrUtil.maxLength(contractText, 1000);
        String detectText = fileName + " " + String.join(" ", result.getHeaders()) + " " + contractText;
        return new ParsedPayload(rawText, detectText, parsed);
    }

    // ==================== 文件类型判断 ====================

    private boolean isExcel(String fn) {
        return fn.endsWith(".xls") || fn.endsWith(".xlsx");
    }

    private boolean isCsv(String fn) {
        return fn.endsWith(".csv");
    }

    private boolean isImageOrPdf(String fn) {
        return fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png")
                || fn.endsWith(".gif") || fn.endsWith(".bmp") || fn.endsWith(".webp")
                || fn.endsWith(".pdf");
    }

    private boolean shouldUseLeaseContractParser(String fname, String text, ImportRecordDO importRecord,
                                                 String currentFormType) {
        String detectedFormType = importRecord != null ? importRecord.getDetectedFormType() : null;
        if (FormTypeConstants.LEASE_CONTRACT.equals(detectedFormType)) {
            return true;
        }
        if (StrUtil.isNotBlank(currentFormType) && !FormTypeConstants.LEASE_CONTRACT.equals(currentFormType)) {
            return false;
        }
        if ((isExcel(fname) || isCsv(fname)) && !hasStrongLeaseContractSignals(text)) {
            return false;
        }
        if (isImageOrPdf(fname) && isLikelyLeaseContractFile(fname)) {
            return true;
        }
        if (StrUtil.isBlank(text)) {
            return false;
        }
        return hasStrongLeaseContractSignals(text);
    }

    private boolean isLikelyLeaseContractFile(String fn) {
        return StrUtil.isNotBlank(fn) && (fn.contains("租赁合同")
                || fn.contains("房屋租赁")
                || fn.contains("租房合同")
                || fn.contains("出租合同")
                || fn.contains("出租")
                || fn.contains("合同")
                || fn.contains("lease-contract")
                || fn.contains("lease_contract"));
    }

    private boolean hasStrongLeaseContractSignals(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        boolean hasLessor = containsAny(text, "出租方", "甲方");
        boolean hasLessee = containsAny(text, "承租方", "乙方");
        boolean hasPeriod = containsAny(text, "租赁期限", "租赁期", "租赁期自", "起至");
        boolean hasRent = containsAny(text, "房屋租金", "年租金", "租金");
        int score = 0;
        if (containsAny(text, "房屋租赁合同", "租赁合同", "出租合同", "综合楼出租")) score++;
        if (hasLessor) score++;
        if (hasLessee) score++;
        if (hasPeriod) score++;
        if (hasRent) score++;
        if (containsAny(text, "保证金", "押金")) score++;
        if (containsAny(text, "签订日期", "合同签订日期", "签约日期")) score++;
        return score >= 4 && hasLessor && hasLessee && (hasPeriod || hasRent);
    }

    // ==================== 表单类型识别 ====================

    @SuppressWarnings("unchecked")
    private String detectFormType(ParsedPayload payload, String fileName) {
        List<String> headers = Collections.emptyList();
        String sheetName = null;
        Object headersObj = payload.parsedData.get("headers");
        if (headersObj instanceof List) {
            headers = (List<String>) headersObj;
        }
        Object sheetObj = payload.parsedData.get("sheetName");
        if (sheetObj != null) {
            sheetName = sheetObj.toString();
        }
        JijianFormTypeAutoDetectService.DetectResult result =
                formTypeAutoDetectService.detect(fileName, sheetName, headers);
        if (StrUtil.isNotBlank(result.detectedFormType)) {
            return result.detectedFormType;
        }
        return detectFormType(payload.detectText);
    }

    private String detectFormType(String text) {
        if (StrUtil.isBlank(text)) return null;
        String c = text;
        if (containsAny(c, "营业执照", "是否内部人员", "身份证号", "联系电话")) return FormTypeConstants.LESSEE;
        if (containsAny(c, "疗养假", "疗休养", "休假地点", "参加工作时间", "工作年限")) return FormTypeConstants.LEAVE_HEALTH;
        if (containsAny(c, "调休", "加班开始", "调休开始", "调休时长", "补休")) return FormTypeConstants.COMPENSATORY;
        if (containsAny(c, "出差", "出差事由", "出差类型", "出差开始")) return FormTypeConstants.BUSINESS_TRIP;
        if (containsAny(c, "事假", "请假类型", "请假事由", "请假开始", "假期类型")) return FormTypeConstants.LEAVE_PERSONAL;
        if (containsAny(c, "考勤", "打卡", "上班打卡", "下班打卡", "考勤日期")) return FormTypeConstants.ATTENDANCE;
        if (containsAny(c, "义乌市民生商品市场零售价格信息公告", "民生商品市场零售价格", "价格信息公告")
                || (containsAny(c, "项目名称", "规格/等级", "规格、等级")
                && containsAny(c, "采价点", "价格"))) {
            return FormTypeConstants.CANTEEN_MARKET_PRICE;
        }
        // 食堂：采价点/采购点/商品名称/配送单/供应商也作为识别依据
        if (containsAny(c, "食堂", "食堂配送单", "物品名称", "商品名称",
                         "采购点", "采购地点", "配送单", "供应商", "供货商", "单价", "小计")) return FormTypeConstants.CANTEEN;
        if (hasStrongLeaseContractSignals(c)) return FormTypeConstants.LEASE_CONTRACT;
        if (containsAny(c, "房产", "不动产", "产权", "建筑面积", "房产地址", "租赁情况")) return FormTypeConstants.PROPERTY;
        if (containsAny(c, "地址", "面积")) return FormTypeConstants.PROPERTY;
        return null;
    }

    private boolean containsAny(String content, String... keywords) {
        for (String kw : keywords) {
            if (content.contains(kw)) return true;
        }
        return false;
    }

    // ==================== 工具方法 ====================

    private List<List<String>> readSheetRows(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<List<String>> rows = new ArrayList<>();
        int maxColumn = 0;
        for (Row row : sheet) {
            maxColumn = Math.max(maxColumn, Math.max(row.getLastCellNum(), 0));
        }
        for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            List<String> values = new ArrayList<>();
            for (int column = 0; column < maxColumn; column++) {
                values.add(readCellValue(sheet, rowIndex, column, formatter, evaluator));
            }
            rows.add(values);
        }
        return rows;
    }

    private String readCellValue(Sheet sheet, int rowIndex, int column,
            DataFormatter formatter, FormulaEvaluator evaluator) {
        Cell cell = getCell(sheet, rowIndex, column);
        if (cell == null) {
            return "";
        }
        try {
            return StrUtil.trim(formatter.formatCellValue(cell, evaluator));
        } catch (Exception ignore) {
            return StrUtil.trim(formatter.formatCellValue(cell));
        }
    }

    private Cell getCell(Sheet sheet, int rowIndex, int column) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : row.getCell(column);
    }

    private String decodeBytes(byte[] bytes) {
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF
                && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        try {
            String utf8 = new String(bytes, StandardCharsets.UTF_8);
            if (!utf8.contains("")) return utf8;
        } catch (Exception ignored) {}
        try {
            return new String(bytes, Charset.forName("GBK"));
        } catch (Exception ignored) {}
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Map<String, Object> buildParsedMap(String fileName, String sheetName,
            List<String> headers, List<Map<String, String>> rows, int totalRows) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("fileName", fileName);
        parsed.put("sheetName", sheetName);
        parsed.put("headers", headers);
        parsed.put("rows", rows);
        parsed.put("totalRows", totalRows);
        return parsed;
    }

    private String buildRawText(String fileName, String sheetName,
            List<String> headers, List<Map<String, String>> rows, int totalRows) {
        StringBuilder sb = new StringBuilder();
        sb.append("文件：").append(fileName)
          .append(" | 来源：").append(sheetName)
          .append(" | 表头：").append(String.join("，", headers))
          .append(" | 共").append(totalRows).append("行");
        if (!rows.isEmpty()) {
            sb.append(" | 首行：");
            int cnt = 0;
            for (Map.Entry<String, String> e : rows.get(0).entrySet()) {
                if (cnt++ >= DETECT_SAMPLE_COLS) break;
                sb.append(e.getKey()).append("=").append(StrUtil.maxLength(e.getValue(), 15)).append("；");
            }
        }
        return sb.toString();
    }

    /**
     * 考勤日报专用：将重复的"打卡时间/打卡结果/打卡地点"按上班/下班语义重命名。
     * 规则：以"上班备注"列为分界，之前的归入上班打卡，之后的归入下班打卡。
     * 若无"上班备注"，则以 _2 后缀区分第二组。
     */
    @SuppressWarnings("unchecked")
    private void normalizeAttendanceHeaders(Map<String, Object> parsedData) {
        Object headersObj = parsedData.get("headers");
        if (!(headersObj instanceof List)) return;
        List<String> headers = (List<String>) headersObj;

        int checkinRemarkIdx = -1;
        for (int i = 0; i < headers.size(); i++) {
            if ("上班备注".equals(headers.get(i))) {
                checkinRemarkIdx = i;
                break;
            }
        }

        Map<String, String> renameMap = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String h = headers.get(i);
            String baseName = h.endsWith("_2") ? h.substring(0, h.length() - 2) : h;
            boolean isCheckin;
            if (checkinRemarkIdx >= 0) {
                isCheckin = i < checkinRemarkIdx;
            } else {
                isCheckin = !h.endsWith("_2");
            }
            String renamed = null;
            if ("打卡时间".equals(baseName)) renamed = isCheckin ? "上班打卡时间" : "下班打卡时间";
            else if ("打卡结果".equals(baseName)) renamed = isCheckin ? "上班打卡结果" : "下班打卡结果";
            else if ("打卡地点".equals(baseName)) renamed = isCheckin ? "上班打卡地点" : "下班打卡地点";
            if (renamed != null) {
                renameMap.put(h, renamed);
                headers.set(i, renamed);
            }
        }

        if (renameMap.isEmpty()) return;
        Object rowsObj = parsedData.get("rows");
        if (!(rowsObj instanceof List)) return;
        List<Map<String, String>> rows = (List<Map<String, String>>) rowsObj;
        for (Map<String, String> row : rows) {
            for (Map.Entry<String, String> entry : renameMap.entrySet()) {
                if (row.containsKey(entry.getKey())) {
                    String val = row.remove(entry.getKey());
                    row.put(entry.getValue(), val);
                }
            }
        }
    }

    private String extractHeadersHint(ParsedPayload payload) {
        try {
            Object h = payload.parsedData.get("headers");
            if (h instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) h;
                return String.join("，", headers);
            }
        } catch (Exception ignore) {}
        return "";
    }

    private void updateImportRecord(ImportRecordDO importRecord, String detectedFormType, String status) {
        importRecord.setDetectedFormType(StrUtil.blankToDefault(detectedFormType, "未知类型"));
        importRecord.setStatus(status);
        importRecordMapper.updateById(importRecord);
    }

    private void updateImportRecordStatus(Long importRecordId, String detectedFormType, String status) {
        if (importRecordId == null) {
            return;
        }
        ImportRecordDO update = new ImportRecordDO();
        update.setId(importRecordId);
        update.setDetectedFormType(StrUtil.blankToDefault(detectedFormType, "未知类型"));
        update.setStatus(status);
        importRecordMapper.updateById(update);
    }

    private Long parseLong(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return null; }
    }

    private static class ParsedPayload {
        final String rawText;
        final String detectText;
        final Map<String, Object> parsedData;
        ParsedPayload(String rawText, String detectText, Map<String, Object> parsedData) {
            this.rawText = rawText;
            this.detectText = detectText;
            this.parsedData = parsedData;
        }
    }
}
