package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.importrecord.ImportRecordMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.parseddata.ParsedDataMapper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Validated
public class ParsedDataServiceImpl implements ParsedDataService {

    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_FAILED = "failed";

    @Resource
    private ParsedDataMapper parsedDataMapper;
    @Resource
    private ImportRecordMapper importRecordMapper;

    @Override
    public ParsedDataDO parseAndCreate(ImportRecordDO importRecord, MultipartFile file) {
        try {
            ParsedPayload payload = isExcel(file.getOriginalFilename()) ? parseExcel(file) : parseGeneric(file);
            String formType = detectFormType(payload.detectText);
            ParsedDataDO parsedData = ParsedDataDO.builder()
                    .importRecordId(importRecord.getId())
                    .formType(formType)
                    .rawText(payload.rawText)
                    .parsedJson(JSONUtil.toJsonStr(payload.parsedData))
                    .confidence(new BigDecimal("0.86"))
                    .status(STATUS_SUCCESS)
                    .build();
            parsedDataMapper.insert(parsedData);
            updateImportRecord(importRecord, formType, STATUS_SUCCESS);
            return parsedData;
        } catch (Exception ex) {
            return createFailedParsedData(importRecord, StrUtil.blankToDefault(ex.getMessage(), "文件解析失败"));
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
    public ParsedDataDO getLatestParsedData(Long importRecordId) {
        return parsedDataMapper.selectLatestByImportRecordId(importRecordId);
    }

    @Override
    public List<ParsedDataDO> getParsedDataList(Long importRecordId) {
        return parsedDataMapper.selectListByImportRecordId(importRecordId);
    }

    private void updateImportRecord(ImportRecordDO importRecord, String detectedFormType, String status) {
        importRecord.setDetectedFormType(StrUtil.blankToDefault(detectedFormType, "未知类型"));
        importRecord.setStatus(status);
        importRecordMapper.updateById(importRecord);
    }

    private ParsedPayload parseGeneric(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        String rawText = readTextPreview(file);
        if (StrUtil.isBlank(rawText)) {
            rawText = "文件名：" + fileName + "\n当前为过渡解析逻辑，后续可接入 OCR、PDF、Word 文档解析服务。";
        }
        Map<String, Object> parsed = new LinkedHashMap<>();
        parsed.put("fileName", fileName);
        parsed.put("source", "ocr_or_document");
        parsed.put("textPreview", StrUtil.maxLength(rawText, 1000));
        parsed.put("fields", buildSimpleFields(rawText + " " + fileName));
        return new ParsedPayload(rawText, rawText + " " + fileName, parsed);
    }

    private ParsedPayload parseExcel(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "未命名文件");
        try (InputStream inputStream = file.getInputStream(); Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                throw new IllegalArgumentException("Excel 文件没有可读取的 Sheet");
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            Row headerRow = sheet.getRow(sheet.getFirstRowNum());
            List<String> headers = readRow(headerRow, formatter);
            List<Map<String, String>> rows = new ArrayList<>();
            int lastRow = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 5);
            for (int rowIndex = sheet.getFirstRowNum() + 1; rowIndex <= lastRow; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }
                List<String> values = readRow(row, formatter);
                if (values.stream().allMatch(StrUtil::isBlank)) {
                    continue;
                }
                Map<String, String> rowData = new LinkedHashMap<>();
                int columnCount = Math.max(headers.size(), values.size());
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    String header = columnIndex < headers.size() && StrUtil.isNotBlank(headers.get(columnIndex))
                            ? headers.get(columnIndex) : "column" + (columnIndex + 1);
                    rowData.put(header, columnIndex < values.size() ? values.get(columnIndex) : "");
                }
                rows.add(rowData);
            }
            Map<String, Object> parsed = new LinkedHashMap<>();
            parsed.put("fileName", fileName);
            parsed.put("sheetName", sheet.getSheetName());
            parsed.put("headers", headers);
            parsed.put("rows", rows);
            String rawText = "Sheet：" + sheet.getSheetName() + "\n表头：" + String.join("，", headers)
                    + "\n样例数据：" + JSONUtil.toJsonStr(rows);
            return new ParsedPayload(rawText, fileName + " " + String.join(" ", headers) + " " + JSONUtil.toJsonStr(rows), parsed);
        }
    }

    private List<String> readRow(Row row, DataFormatter formatter) {
        List<String> values = new ArrayList<>();
        if (row == null) {
            return values;
        }
        short lastCellNum = row.getLastCellNum();
        if (lastCellNum < 0) {
            return values;
        }
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            Cell cell = row.getCell(cellIndex);
            values.add(cell == null ? "" : StrUtil.trim(formatter.formatCellValue(cell)));
        }
        return values;
    }

    private String readTextPreview(MultipartFile file) throws Exception {
        String fileName = StrUtil.blankToDefault(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        if (!(fileName.endsWith(".txt") || fileName.endsWith(".csv") || fileName.endsWith(".md") || fileName.endsWith(".log"))) {
            return "";
        }
        byte[] bytes = file.getBytes();
        int length = Math.min(bytes.length, 4096);
        return new String(bytes, 0, length, StandardCharsets.UTF_8);
    }

    private boolean isExcel(String fileName) {
        String lowerFileName = StrUtil.blankToDefault(fileName, "").toLowerCase(Locale.ROOT);
        return lowerFileName.endsWith(".xls") || lowerFileName.endsWith(".xlsx");
    }

    private String detectFormType(String text) {
        String content = StrUtil.blankToDefault(text, "");
        if (containsAny(content, "房产", "地址", "面积", "不动产")) {
            return "房产信息";
        }
        if (containsAny(content, "考勤", "打卡", "迟到", "早退")) {
            return "考勤信息";
        }
        if (containsAny(content, "合同", "甲方", "乙方", "金额")) {
            return "合同管理";
        }
        if (containsAny(content, "报销", "费用", "发票")) {
            return "报销信息";
        }
        if (containsAny(content, "姓名", "身份证", "部门")) {
            return "人员信息";
        }
        return "人员信息";
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> buildSimpleFields(String text) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("hasPropertyKeyword", containsAny(text, "房产", "地址", "面积", "不动产"));
        fields.put("hasAttendanceKeyword", containsAny(text, "考勤", "打卡", "迟到", "早退"));
        fields.put("hasContractKeyword", containsAny(text, "合同", "甲方", "乙方", "金额"));
        fields.put("hasExpenseKeyword", containsAny(text, "报销", "费用", "发票"));
        fields.put("hasPersonKeyword", containsAny(text, "姓名", "身份证", "部门"));
        return fields;
    }

    private static class ParsedPayload {

        private final String rawText;
        private final String detectText;
        private final Map<String, Object> parsedData;

        private ParsedPayload(String rawText, String detectText, Map<String, Object> parsedData) {
            this.rawText = rawText;
            this.detectText = detectText;
            this.parsedData = parsedData;
        }

    }

}
