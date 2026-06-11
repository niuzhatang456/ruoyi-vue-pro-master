package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves tabular imports without assuming the first row is the header.
 */
public final class TableStructureResolver {

    private static final int HEADER_SCAN_LIMIT = 15;
    private static final Pattern DATA_LIKE = Pattern.compile(
            ".*(?:\\d{4}[-年/.]\\d{1,2}|\\d+(?:\\.\\d+)?(?:元|号|日|月)?|1\\d{10}).*");
    private static final List<List<String>> HEADER_ALIASES = Arrays.asList(
            Arrays.asList("序号", "编号", "no"),
            Arrays.asList("备注", "说明"),
            Arrays.asList("商品名称", "品名", "项目名称", "物品名称", "名称"),
            Arrays.asList("规格", "规格型号", "型号", "等级", "规格等级", "规格、等级", "规格/等级"),
            Arrays.asList("单位"),
            Arrays.asList("数量", "重量"),
            Arrays.asList("单价", "价格"),
            Arrays.asList("小计", "金额", "合计金额"),
            Arrays.asList("采价点", "采样点", "采购点", "价格采集点"),
            Arrays.asList("部门", "所在部门", "科室"),
            Arrays.asList("申请人", "姓名", "人员", "员工"),
            Arrays.asList("休假地点", "地点", "目的地"),
            Arrays.asList("疗养假开始时间", "休假开始时间", "开始时间", "开始日期"),
            Arrays.asList("疗养假结束时间", "休假结束时间", "结束时间", "结束日期"),
            Arrays.asList("事由", "原因", "请假事由"),
            Arrays.asList("天数", "请假天数", "休假天数"),
            Arrays.asList("打卡时间", "打卡结果", "打卡地点", "上班备注", "下班备注"),
            Arrays.asList("房产地址", "地址"),
            Arrays.asList("房产名称"),
            Arrays.asList("产权信息", "产权"),
            Arrays.asList("面积"),
            Arrays.asList("租赁情况"),
            Arrays.asList("出租方", "甲方", "出租人"),
            Arrays.asList("承租方", "乙方", "承租人"),
            Arrays.asList("合同编号", "合同号"),
            Arrays.asList("租金", "月租", "租费"),
            Arrays.asList("支付情况", "付款情况", "支付状态"),
            Arrays.asList("合同期", "合同期限", "租期"),
            Arrays.asList("合同内容摘要", "合同摘要", "摘要", "合同内容"));
    private static final List<String> REJECT_WORDS = Arrays.asList(
            "日期：", "日期:", "发布时间", "信息来源", "访问次数", "配送单", "公告", "标题");
    private static final List<String> SUMMARY_WORDS = Arrays.asList("小计", "合计", "总计");
    /** 仅用于数据行跳过：供应商落款行、客户签字行等非商品行 */
    private static final List<String> DATA_REJECT_WORDS = Arrays.asList(
            "供应商：", "供应商:", "供货商：", "供货商:", "配送单位：", "配送单位:",
            "客户签字", "签字：", "签字:", "签名：", "签名:");

    private TableStructureResolver() {
    }

    public static Result resolve(List<List<String>> rawRows) {
        List<List<String>> rows = rawRows.stream()
                .map(TableStructureResolver::cleanRow)
                .collect(Collectors.toList());
        int headerIndex = findHeaderIndex(rows);
        if (headerIndex < 0) {
            throw new IllegalArgumentException("未识别到有效表头，请检查文件内容或改用清晰文件上传");
        }

        int dataStartIndex = headerIndex + 1;
        List<String> headerRow = rows.get(headerIndex);
        if (dataStartIndex < rows.size() && shouldMergeSecondaryHeader(headerRow, rows.get(dataStartIndex))) {
            headerRow = mergeHeaders(headerRow, rows.get(dataStartIndex));
            dataStartIndex++;
        }
        List<HeaderColumn> headerColumns = makeUniqueHeaderColumns(headerRow);
        List<String> headers = headerColumns.stream().map(HeaderColumn::getName).collect(Collectors.toList());

        List<Map<String, String>> mappedRows = new ArrayList<>();
        Map<Integer, String> fillDownValues = new LinkedHashMap<>();
        for (int rowIndex = dataStartIndex; rowIndex < rows.size(); rowIndex++) {
            List<String> values = rows.get(rowIndex);
            if (shouldSkipDataRow(values)) {
                continue;
            }
            Map<String, String> mapped = new LinkedHashMap<>();
            int nonBlankCount = 0;
            for (HeaderColumn headerColumn : headerColumns) {
                int column = headerColumn.getColumnIndex();
                String header = headerColumn.getName();
                String value = column < values.size() ? values.get(column) : "";
                if (StrUtil.isBlank(value) && shouldFillDown(header)) {
                    value = fillDownValues.getOrDefault(column, "");
                }
                if (StrUtil.isNotBlank(value)) {
                    nonBlankCount++;
                    if (shouldFillDown(header)) {
                        fillDownValues.put(column, value);
                    }
                }
                mapped.put(header, value);
            }
            if (nonBlankCount >= 2 && hasRequiredDescriptiveValue(mapped)) {
                mappedRows.add(mapped);
            }
        }
        if (mappedRows.isEmpty()) {
            throw new IllegalArgumentException("已识别到有效表头，但未识别到有效数据行");
        }
        return new Result(headerIndex, headers, mappedRows);
    }

    private static int findHeaderIndex(List<List<String>> rows) {
        int bestIndex = -1;
        int bestScore = Integer.MIN_VALUE;
        int limit = Math.min(rows.size(), HEADER_SCAN_LIMIT);
        for (int i = 0; i < limit; i++) {
            int score = scoreHeader(rows.get(i));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestScore < 20) {
            return -1;
        }
        if (bestIndex > 0 && shouldMergeSecondaryHeader(rows.get(bestIndex - 1), rows.get(bestIndex))) {
            return bestIndex - 1;
        }
        return bestIndex;
    }

    private static int scoreHeader(List<String> cells) {
        List<String> nonBlank = cells.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
        if (nonBlank.size() < 2) {
            return Integer.MIN_VALUE;
        }
        String joined = String.join("", nonBlank);
        if (containsAny(joined, REJECT_WORDS)) {
            return Integer.MIN_VALUE;
        }
        Set<Integer> matchedGroups = new HashSet<>();
        int dataPenalty = 0;
        for (String cell : nonBlank) {
            String normalizedCell = normalize(cell);
            if (DATA_LIKE.matcher(normalizedCell).matches()) {
                dataPenalty += 2;
            }
            for (int i = 0; i < HEADER_ALIASES.size(); i++) {
                for (String alias : HEADER_ALIASES.get(i)) {
                    String normalizedAlias = normalize(alias);
                    if (normalizedCell.equals(normalizedAlias) || normalizedCell.contains(normalizedAlias)) {
                        matchedGroups.add(i);
                        break;
                    }
                }
            }
        }
        return matchedGroups.size() * 10 - dataPenalty;
    }

    private static boolean shouldMergeSecondaryHeader(List<String> headers, List<String> nextRow) {
        if (nextRow.stream().filter(StrUtil::isNotBlank).count() < 2) {
            return false;
        }
        boolean parentHeader = headers.stream().anyMatch(header ->
                normalize(header).matches(".*(?:上班卡|下班卡|上午|下午|上班|下班)\\d*.*"));
        return parentHeader && scoreHeader(nextRow) >= 20;
    }

    private static List<String> mergeHeaders(List<String> parents, List<String> children) {
        List<String> merged = new ArrayList<>();
        int columnCount = Math.max(parents.size(), children.size());
        for (int i = 0; i < columnCount; i++) {
            String parent = i < parents.size() ? parents.get(i) : "";
            String child = i < children.size() ? children.get(i) : "";
            String normalizedParent = normalize(parent).replaceAll("卡\\d+", "");
            String normalizedChild = normalize(child);
            if (StrUtil.isBlank(normalizedChild) || normalizedParent.equals(normalizedChild)) {
                merged.add(parent);
            } else if (normalizedChild.startsWith(normalizedParent)) {
                merged.add(child);
            } else if (StrUtil.isBlank(normalizedParent)) {
                merged.add(child);
            } else {
                merged.add(normalizedParent + child);
            }
        }
        return merged;
    }

    private static boolean shouldSkipDataRow(List<String> cells) {
        List<String> nonBlank = cells.stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
        if (nonBlank.isEmpty()) {
            return true;
        }
        String joined = String.join("", nonBlank);
        if (containsAny(joined, SUMMARY_WORDS)) {
            return true;
        }
        if (containsAny(joined, REJECT_WORDS)) {
            return true;
        }
        if (containsAny(joined, DATA_REJECT_WORDS)) {
            return true;
        }
        return scoreHeader(cells) >= 20;
    }

    private static boolean shouldFillDown(String header) {
        String normalized = normalize(header);
        // 注意：姓名/员工编号不做 fillDown（合并单元格由 JijianExcelMergedCellUtils 展开处理，
        // 此处 fillDown 仅用于"同一天跨多行共享字段"场景，不应填充到不同人的行中）
        return containsAny(normalized, Arrays.asList("采价点", "采样点", "采购点", "价格采集点",
                "部门", "所在部门", "科室",
                "日期", "考勤日期", "打卡日期",
                "星期"));
    }

    private static List<String> cleanRow(List<String> row) {
        List<String> cleaned = new ArrayList<>();
        for (String value : row) {
            cleaned.add(StrUtil.blankToDefault(value, "").trim().replaceAll("\\s+", " "));
        }
        return cleaned;
    }

    private static List<HeaderColumn> makeUniqueHeaderColumns(List<String> headers) {
        List<HeaderColumn> result = new ArrayList<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (StrUtil.isBlank(header)) {
                continue;
            }
            int count = seen.getOrDefault(header, 0) + 1;
            seen.put(header, count);
            result.add(new HeaderColumn(i, count == 1 ? header : header + "_" + count));
        }
        return result;
    }

    private static boolean hasRequiredDescriptiveValue(Map<String, String> row) {
        boolean hasDescriptiveHeader = false;
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String normalized = normalize(entry.getKey());
            if (containsAny(normalized, Arrays.asList("商品名称", "品名", "项目名称", "物品名称"))) {
                hasDescriptiveHeader = true;
                if (StrUtil.isNotBlank(entry.getValue())) {
                    return true;
                }
            }
        }
        return !hasDescriptiveHeader;
    }

    private static String normalize(String value) {
        return StrUtil.blankToDefault(value, "").toLowerCase()
                .replaceAll("[\\s，,、/：:（）()]+", "");
    }

    private static boolean containsAny(String text, List<String> words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    @Data
    @AllArgsConstructor
    public static class Result {
        private int headerIndex;
        private List<String> headers;
        private List<Map<String, String>> rows;
    }

    @Data
    @AllArgsConstructor
    private static class HeaderColumn {
        private int columnIndex;
        private String name;
    }
}
