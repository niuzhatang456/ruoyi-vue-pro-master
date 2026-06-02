package cn.iocoder.yudao.module.jijian.service.parseddata;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TableStructureResolverTest {

    @Test
    void shouldResolveCanteenRowsAfterSubtotal() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("义乌市融媒体中心食堂配送单"),
                row("日期：2025年3月4号"),
                row("序号", "商品名称", "规格", "单位", "数量", "单价", "小计", "备注"),
                row("1", "大米", "一级", "袋", "2", "88", "176", "1件"),
                row("", "", "", "", "", "", "小计", "176"),
                row("2", "鸡蛋", "新鲜", "箱", "3", "60", "180", "3个")));

        assertEquals(2, result.getHeaderIndex());
        assertEquals(2, result.getRows().size());
        assertEquals("鸡蛋", result.getRows().get(1).get("商品名称"));
        assertEquals("3个", result.getRows().get(1).get("备注"));
    }

    @Test
    void shouldFillDownPurchasePoint() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("序号", "项目名称", "规格、等级", "单位", "价格", "采价点"),
                row("1", "籼米", "一级", "元/500克", "2.98", "美一天生活超市北苑店"),
                row("2", "粳米", "一级", "元/500克", "2.88", "")));

        assertEquals("美一天生活超市北苑店", result.getRows().get(1).get("采价点"));
    }

    @Test
    void shouldResolveLeaveHealthHeader() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("疗养假申请表"),
                row("部门", "申请人", "休假地点", "疗养假开始时间", "疗养假结束时间"),
                row("社文部", "毛泽忠(78253228)", "义乌", "2026-03-23", "2026-03-27")));

        assertEquals(1, result.getHeaderIndex());
        assertEquals("毛泽忠(78253228)", result.getRows().get(0).get("申请人"));
    }

    @Test
    void shouldMergeAttendanceHeaders() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("姓名", "部门", "上班卡1", "上班卡1", "下班卡1", "下班卡1"),
                row("姓名", "部门", "打卡时间", "打卡结果", "打卡时间", "打卡结果"),
                row("陈华栋", "中心领导", "08:30", "正常", "17:30", "正常")));

        List<String> headers = result.getHeaders();
        assertEquals("上班打卡时间", headers.get(2));
        assertEquals("下班打卡结果", headers.get(5));
    }

    @Test
    void shouldNotDuplicateAttendancePrefix() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("姓名", "部门", "上班", "上班", "下班", "下班"),
                row("姓名", "部门", "打卡时间", "上班备注", "打卡时间", "下班备注"),
                row("陈华栋", "中心领导", "08:30", "", "17:30", "")));

        assertEquals("上班备注", result.getHeaders().get(3));
        assertEquals("下班备注", result.getHeaders().get(5));
    }

    @Test
    void shouldIgnoreBlankHeaderColumnsWithoutShiftingValues() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("义乌市融媒体中心食堂配送单", "", "", "", "", "", "", ""),
                row("", "", "", "", "日期：2025年2月5日", "", "", ""),
                row("序号", "商品名称", "规格", "单位", "数量", "单价", "小计", "备注", "", ""),
                row("1", "大生姜", "散装", "斤", "5", "12", "60", "", "", ""),
                row("小计", "", "", "", "", "", "1691.9", "", "", ""),
                row("1", "杀好小公鸡", "散装", "斤", "4", "18", "72", "", "", "")));

        assertEquals(Arrays.asList("序号", "商品名称", "规格", "单位", "数量", "单价", "小计", "备注"),
                result.getHeaders());
        assertEquals("大生姜", result.getRows().get(0).get("商品名称"));
        assertEquals("60", result.getRows().get(0).get("小计"));
        assertEquals("杀好小公鸡", result.getRows().get(1).get("商品名称"));
    }

    @Test
    void shouldRejectTitleOnlyRowsInsteadOfFallingBack() {
        assertThrows(IllegalArgumentException.class, () -> TableStructureResolver.resolve(Arrays.asList(
                row("义乌市融媒体中心食堂配送单", "", "", ""),
                row("日期：2025年2月5日", "", "", ""),
                row("1", "大生姜", "散装", "斤"))));
    }

    @Test
    void shouldKeepMissingSequenceColumnEmptyWithoutLeftShift() {
        TableStructureResolver.Result result = TableStructureResolver.resolve(Arrays.asList(
                row("义乌市民生商品市场零售价格信息公告", ""),
                row("序号", "项目名称", "规格、等级", "单位", "价格", "采价点"),
                row("", "灿米 (散装)", "一级", "元/500克", "2.98", "")));

        assertEquals("", result.getRows().get(0).get("序号"));
        assertEquals("灿米 (散装)", result.getRows().get(0).get("项目名称"));
        assertEquals("2.98", result.getRows().get(0).get("价格"));
    }

    @Test
    void shouldResolveConfiguredRealExcelFile() throws Exception {
        String fileName = System.getProperty("jijian.realExcel");
        Assumptions.assumeTrue(fileName != null && !fileName.isEmpty());
        Path file = Paths.get(fileName);
        Assumptions.assumeTrue(Files.isRegularFile(file));

        TableStructureResolver.Result result = resolveExcel(file);
        System.out.println("REAL_EXCEL_HEADERS=" + result.getHeaders());
        System.out.println("REAL_EXCEL_FIRST_ROW=" + result.getRows().get(0));
        System.out.println("REAL_EXCEL_ROWS=" + result.getRows().size());
        System.out.println("REAL_EXCEL_ALL_ROWS=" + result.getRows());
        assertEquals(Arrays.asList("序号", "商品名称", "规格", "单位", "数量", "单价", "小计", "备注"),
                result.getHeaders());
        assertTrue(result.getRows().stream().noneMatch(row -> row.containsValue("义乌市融媒体中心食堂配送单")));
        assertTrue(result.getRows().stream().noneMatch(row -> "小计".equals(row.get("序号")) || "总计".equals(row.get("序号"))));
        assertTrue(result.getRows().stream().anyMatch(row -> "老南瓜".equals(row.get("商品名称")) && "3个".equals(row.get("备注"))));
    }

    @Test
    void shouldInspectConfiguredExcelFiles() throws Exception {
        String fileNames = System.getProperty("jijian.inspectExcel");
        Assumptions.assumeTrue(fileNames != null && !fileNames.isEmpty());
        for (String fileName : fileNames.split(",")) {
            Path file = Paths.get(fileName);
            Assumptions.assumeTrue(Files.isRegularFile(file));
            TableStructureResolver.Result result = resolveExcel(file);
            System.out.println("INSPECT_EXCEL_FILE=" + file.getFileName());
            System.out.println("INSPECT_EXCEL_HEADERS=" + result.getHeaders());
            System.out.println("INSPECT_EXCEL_FIRST_ROW=" + result.getRows().get(0));
            System.out.println("INSPECT_EXCEL_ROWS=" + result.getRows().size());
            assertTrue(!result.getHeaders().isEmpty());
            assertTrue(!result.getRows().isEmpty());
        }
    }

    private static TableStructureResolver.Result resolveExcel(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<List<String>> rows = new ArrayList<>();
            int maxColumn = 0;
            for (Row row : sheet) {
                maxColumn = Math.max(maxColumn, Math.max(row.getLastCellNum(), 0));
            }
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                List<String> values = new ArrayList<>();
                Row row = sheet.getRow(rowIndex);
                for (int column = 0; column < maxColumn; column++) {
                    values.add(row == null || row.getCell(column) == null ? ""
                            : formatter.formatCellValue(row.getCell(column), evaluator).trim());
                }
                rows.add(values);
            }
            return TableStructureResolver.resolve(rows);
        }
    }

    private static List<String> row(String... values) {
        return Arrays.asList(values);
    }
}
