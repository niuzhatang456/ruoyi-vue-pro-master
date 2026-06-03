package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
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
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

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

    @Resource private ParsedDataMapper            parsedDataMapper;
    @Resource private ImportRecordMapper          importRecordMapper;
    @Resource private ConfirmWriteHandlerRegistry handlerRegistry;
    @Resource private JijianProperties            jijianProperties;
    @Resource private OcrService                  ocrService;

    // ==================== 上传解析入口 ====================

    @Override
    public ParsedDataDO parseAndCreate(ImportRecordDO importRecord, MultipartFile file) {
        try {
            String fname = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
            ParsedPayload payload;

            if (isExcel(fname)) {
                payload = parseExcel(file);
            } else if (isCsv(fname)) {
                payload = parseCsv(file);
            } else if (isImageOrPdf(fname)) {
                payload = parseImageOrPdf(file, fname);
            } else {
                payload = parseTextFile(file);
            }

            String formType = detectFormType(payload.detectText);
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
        ConfirmWriteResult result = handler.doConfirm(parsedData);

        // 回写追溯字段（confirm_status 已在 CAS 中更新，此处补其余字段）
        Long userId = null;
        try { userId = SecurityFrameworkUtils.getLoginUserId(); } catch (Exception ignore) {}

        ParsedDataDO update = new ParsedDataDO();
        update.setId(parsedDataId);
        update.setConfirmTime(LocalDateTime.now());
        update.setConfirmUserId(userId);
        update.setBusinessTable(result.getBusinessTable() != null
                ? result.getBusinessTable() : handler.getBusinessTableName());
        update.setBusinessIds(JSONUtil.toJsonStr(result.getConfirmedIds()));
        if (FormTypeConstants.PROPERTY.equals(formType) && !result.getConfirmedIds().isEmpty()) {
            update.setConfirmedPropertyId(result.getConfirmedIds().get(0));
        }
        parsedDataMapper.updateById(update);
        return result;
    }

    private ConfirmWriteResult buildIdempotentResult(ParsedDataDO parsedData, Long parsedDataId) {
        String existingBusinessTable = parsedData.getBusinessTable();
        List<Long> existingIds = new ArrayList<>();
        if (StrUtil.isNotBlank(parsedData.getBusinessIds())) {
            try {
                cn.hutool.json.JSONArray arr = cn.hutool.json.JSONUtil.parseArray(parsedData.getBusinessIds());
                for (int i = 0; i < arr.size(); i++) existingIds.add(arr.getLong(i));
            } catch (Exception ignore) {}
        }
        if (existingIds.isEmpty()) {
            ConfirmWriteHandler handler = handlerRegistry.getHandler(parsedData.getFormType());
            if (handler != null) {
                existingIds = handler.queryConfirmedSummary(parsedDataId).stream()
                        .map(m -> parseLong(m.get("记录ID")))
                        .filter(id -> id != null)
                        .collect(Collectors.toList());
                if (StrUtil.isBlank(existingBusinessTable)) existingBusinessTable = handler.getBusinessTableName();
            }
        }
        return ConfirmWriteResult.idempotent(parsedData.getFormType(), existingBusinessTable, existingIds);
    }

    @Override
    public Long confirmProperty(Long parsedDataId) {
        ConfirmWriteResult result = confirmWrite(parsedDataId);
        return result.getConfirmedIds().isEmpty() ? null : result.getConfirmedIds().get(0);
    }

    // ==================== Excel 解析 ====================

    private ParsedPayload parseExcel(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        try (InputStream in = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) throw new IllegalArgumentException("Excel 文件没有可读取的 Sheet");

            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            TableStructureResolver.Result resolved = TableStructureResolver.resolve(
                    readSheetRows(sheet, formatter, evaluator));
            List<String> headers = resolved.getHeaders();
            List<Map<String, String>> rows = resolved.getRows();

            int totalRows = rows.size();
            Map<String, Object> parsed = buildParsedMap(fileName, sheet.getSheetName(), headers, rows, totalRows);
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

    // ==================== 文件类型判断 ====================

    private boolean isExcel(String fn) {
        return fn.endsWith(".xls") || fn.endsWith(".xlsx");
    }

    private boolean isCsv(String fn) {
        return fn.endsWith(".csv");
    }

    private boolean isImageOrPdf(String fn) {
        return fn.endsWith(".jpg") || fn.endsWith(".jpeg") || fn.endsWith(".png")
                || fn.endsWith(".gif") || fn.endsWith(".bmp") || fn.endsWith(".pdf");
    }

    // ==================== 表单类型识别 ====================

    private String detectFormType(String text) {
        if (StrUtil.isBlank(text)) return null;
        String c = text;
        if (containsAny(c, "营业执照", "是否内部人员", "身份证号", "联系电话")) return FormTypeConstants.LESSEE;
        if (containsAny(c, "合同内容摘要", "租金", "水电费管理", "支付情况", "合同编号")) return FormTypeConstants.LEASE_CONTRACT;
        if (containsAny(c, "疗养假", "疗休养", "休假地点", "参加工作时间", "工作年限")) return FormTypeConstants.LEAVE_HEALTH;
        if (containsAny(c, "调休", "加班开始", "调休开始", "调休时长", "补休")) return FormTypeConstants.COMPENSATORY;
        if (containsAny(c, "出差", "出差事由", "出差类型", "出差开始")) return FormTypeConstants.BUSINESS_TRIP;
        if (containsAny(c, "事假", "请假类型", "请假事由", "请假开始", "假期类型")) return FormTypeConstants.LEAVE_PERSONAL;
        if (containsAny(c, "考勤", "打卡", "上班打卡", "下班打卡", "考勤日期")) return FormTypeConstants.ATTENDANCE;
        // 食堂：采价点/采购点也作为识别依据
        if (containsAny(c, "食堂", "物品名称", "项目名称", "规格等级", "规格、等级",
                         "采购点", "采价点", "采购地点", "单价", "价格")) return FormTypeConstants.CANTEEN;
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
