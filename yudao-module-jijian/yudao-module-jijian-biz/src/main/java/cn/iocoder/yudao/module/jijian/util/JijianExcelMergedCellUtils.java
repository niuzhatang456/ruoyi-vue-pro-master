package cn.iocoder.yudao.module.jijian.util;

import cn.hutool.core.util.StrUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 纪检模块 Excel 合并单元格预处理工具。
 *
 * <p>在正式解析业务字段之前，先遍历 Sheet 中所有 merged region，
 * 将合并单元格的代表值（左上角优先，若空则找区域内第一个非空单元格）
 * 填入区域内所有空白单元格。
 *
 * <p>适用于所有 9 种业务表单的 Excel 解析前置步骤：
 * PROPERTY_INFO / LESSEE / LEASE_CONTRACT / ATTENDANCE_DAILY /
 * RECUPERATION_LEAVE / PERSONAL_LEAVE / BUSINESS_TRIP /
 * COMPENSATORY_LEAVE / CANTEEN_SUPPLIER
 */
public final class JijianExcelMergedCellUtils {

    private JijianExcelMergedCellUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 读取 Sheet 全部行列数据，自动展开合并单元格。
     *
     * <p>合并规则：
     * <ol>
     *   <li>优先取合并区域左上角单元格的值；</li>
     *   <li>左上角为空则在合并区域内找第一个非空单元格的值；</li>
     *   <li>整个区域均为空则填入空字符串；</li>
     *   <li>支持上下合并、左右合并、多行多列合并。</li>
     * </ol>
     *
     * @param sheet     POI Sheet 对象
     * @param formatter DataFormatter（格式化单元格为字符串）
     * @param evaluator FormulaEvaluator（对公式单元格求值）
     * @return 每行每列的文本值列表，合并单元格已展开
     */
    public static List<List<String>> readSheetRowsWithMerge(
            Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {

        // 1. 构建合并单元格 (rowIndex, colIndex) → 代表值 映射
        Map<Long, String> mergeMap = buildMergeMap(sheet, formatter, evaluator);

        // 2. 确定最大列数（遍历所有行取最大值）
        int maxColumn = 0;
        for (Row row : sheet) {
            maxColumn = Math.max(maxColumn, Math.max(row.getLastCellNum(), 0));
        }

        // 3. 逐行读取单元格值，合并区域单元格使用映射值
        int firstRow = sheet.getFirstRowNum();
        int lastRow  = sheet.getLastRowNum();
        List<List<String>> result = new ArrayList<>(lastRow - firstRow + 1);
        for (int r = firstRow; r <= lastRow; r++) {
            List<String> rowValues = new ArrayList<>(maxColumn);
            for (int c = 0; c < maxColumn; c++) {
                long key = encodeKey(r, c);
                if (mergeMap.containsKey(key)) {
                    rowValues.add(mergeMap.get(key));
                } else {
                    rowValues.add(readRawCell(sheet, r, c, formatter, evaluator));
                }
            }
            result.add(rowValues);
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有方法
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 构建合并单元格映射：对每个 merged region 确定代表值，
     * 并将区域内所有 (row, col) 对应到该代表值。
     */
    private static Map<Long, String> buildMergeMap(
            Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {

        int numRegions = sheet.getNumMergedRegions();
        if (numRegions == 0) {
            return Collections.emptyMap();
        }

        Map<Long, String> map = new HashMap<>(numRegions * 8);
        for (int i = 0; i < numRegions; i++) {
            CellRangeAddress region = sheet.getMergedRegion(i);
            String value = findRepresentativeValue(sheet, region, formatter, evaluator);
            // 将整个合并区域的所有格映射到同一个值
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    map.put(encodeKey(r, c), value);
                }
            }
        }
        return map;
    }

    /**
     * 确定合并区域的代表值：
     * <ol>
     *   <li>优先取左上角单元格；</li>
     *   <li>左上角为空则扫描整个区域找第一个非空值；</li>
     *   <li>全为空返回空字符串。</li>
     * </ol>
     */
    private static String findRepresentativeValue(
            Sheet sheet, CellRangeAddress region,
            DataFormatter formatter, FormulaEvaluator evaluator) {

        // 左上角优先
        String topLeft = readRawCell(
                sheet, region.getFirstRow(), region.getFirstColumn(), formatter, evaluator);
        if (StrUtil.isNotBlank(topLeft)) {
            return topLeft;
        }

        // 左上角为空：扫描区域内所有单元格找第一个非空值
        for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
            for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                if (r == region.getFirstRow() && c == region.getFirstColumn()) {
                    continue; // 已检查过
                }
                String val = readRawCell(sheet, r, c, formatter, evaluator);
                if (StrUtil.isNotBlank(val)) {
                    return val;
                }
            }
        }
        return "";
    }

    /**
     * 读取单个单元格的原始文本（不使用合并映射，直接读 POI 原始值）。
     * 对 BLANK 单元格返回空字符串。
     */
    private static String readRawCell(Sheet sheet, int rowIdx, int col,
                                       DataFormatter formatter, FormulaEvaluator evaluator) {
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            return "";
        }
        Cell cell = row.getCell(col);
        if (cell == null || CellType.BLANK == cell.getCellType()) {
            return "";
        }
        try {
            return StrUtil.trim(formatter.formatCellValue(cell, evaluator));
        } catch (Exception e) {
            try {
                return StrUtil.trim(formatter.formatCellValue(cell));
            } catch (Exception e2) {
                return "";
            }
        }
    }

    /**
     * 将 (row, col) 坐标编码为单个 long，避免 String 拼接的对象开销。
     * 约束：row ≤ 1048576（Excel 最大行数），col ≤ 16384（Excel 最大列数）。
     */
    private static long encodeKey(int row, int col) {
        return ((long) row << 20) | (col & 0xFFFFF);
    }
}
