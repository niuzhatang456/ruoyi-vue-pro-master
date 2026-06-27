package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 租赁合同 OCR 原文解析：输出为现有预览表格可消费的 headers + rows。
 */
@Service
public class LeaseContractParseService {

    public static final String META_ORIGINAL_FILE_NAME = "_originalFileName";
    public static final String META_ORIGINAL_FILE_URL = "_originalFileUrl";
    public static final String META_ORIGINAL_FILE_PATH = "_originalFilePath";
    public static final String META_OCR_RAW_TEXT = "_ocrRawText";
    public static final String META_PARSE_STATUS = "_parseStatus";
    public static final String META_PARSE_ERROR_MSG = "_parseErrorMsg";

    private static final int DEFAULT_RENT_PAIR_COUNT = 5;
    private static final Pattern CONTRACT_NO_PREFIX = Pattern.compile("^([A-Za-z]+\\d+)");
    private static final Pattern ID_CARD = Pattern.compile(
            "[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");
    private static final Pattern MOBILE = Pattern.compile("1[3-9]\\d{9}");
    private static final String DATE_REGEX = "\\d{4}\\s*[年\\-/\\.]\\s*\\d{1,2}\\s*[月\\-/\\.]\\s*\\d{1,2}\\s*[日号]?";
    private static final Pattern CHINESE_DATE = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{4})\\s*[-/\\.]\\s*(\\d{1,2})\\s*[-/\\.]\\s*(\\d{1,2})");
    private static final Pattern COMPACT_DATE = Pattern.compile("(?<!\\d)(\\d{4})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern MONEY_AMOUNT = Pattern.compile("([0-9][0-9,，.]*\\s*元)");
    private static final Pattern MONEY_NUMBER = Pattern.compile("([0-9]+(?:[,，][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+)");
    private static final Pattern YEAR_VALUE = Pattern.compile("(\\d{4})\\s*年\\s*[：:]\\s*([^；;]+)");

    private static final List<String> HEADERS_BEFORE_RENT = Arrays.asList(
            "合同编号", "合同签订日期", "出租方", "承租方", "承租人身份证号", "承租人联系电话",
            "房屋状况", "租赁开始时间", "租赁结束时间", "租赁年份", "租赁用途");
    private static final List<String> HEADERS_AFTER_RENT = Arrays.asList("保证金", "水费", "电费", "备注");
    private static final List<String> UNIT_KEYWORDS = Arrays.asList("公司", "有限公司", "中心", "单位", "合作社", "商行", "经营部", "学校", "支公司");

    public ParseResult parse(String fileName, String rawText, String originalFileUrl, String originalFilePath) {
        String text = normalizeText(rawText);
        Map<String, String> row = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        String contractNo = extractContractNo(fileName);
        String signDate = extractSignDate(text);
        Party lessee = extractLessee(text);
        boolean unitLessee = isUnitName(lessee.name);
        String idCard = unitLessee ? "" : firstMatch(lessee.rawValue, ID_CARD);
        if (!unitLessee && StrUtil.isBlank(idCard)) {
            idCard = firstMatch(text, ID_CARD);
        }
        List<String> leasePeriod = extractLeasePeriod(text);
        String leaseStartDate = leasePeriod.size() > 0 ? leasePeriod.get(0) : "";
        String leaseEndDate = leasePeriod.size() > 1 ? leasePeriod.get(1) : "";
        List<RentGroup> rentGroups = extractRentGroups(text, leaseStartDate);

        row.put("合同编号", contractNo);
        row.put("合同签订日期", signDate);
        row.put("出租方", extractLessor(text));
        row.put("承租方", cleanPartyName(removeFirst(lessee.name, ID_CARD)));
        row.put("承租人身份证号", StrUtil.blankToDefault(idCard, ""));
        row.put("承租人联系电话", extractLesseePhone(text, idCard));
        row.put("房屋状况", extractHouseCondition(text));
        row.put("租赁开始时间", leaseStartDate);
        row.put("租赁结束时间", leaseEndDate);
        row.put("租赁年份", calculateLeaseYears(leaseStartDate, leaseEndDate));
        row.put("租赁用途", extractLeasePurpose(text));

        int rentPairCount = Math.max(DEFAULT_RENT_PAIR_COUNT, rentGroups.size());
        List<String> headers = buildHeaders(rentPairCount);
        for (int i = 0; i < rentPairCount; i++) {
            row.put("房屋租金" + (i + 1), i < rentGroups.size() ? rentGroups.get(i).rentAmount : "");
            row.put("租金交纳日期" + (i + 1), i < rentGroups.size() ? rentGroups.get(i).paymentDate : "");
        }
        row.put("租金明细JSON", buildRentInfoJson(rentGroups));

        row.put("保证金", extractDeposit(text));
        row.put("水费", extractUtilityFee(text, "水费"));
        row.put("电费", extractUtilityFee(text, "电费"));

        addMissingWarning(warnings, "合同编号", contractNo);
        addMissingWarning(warnings, "合同签订日期", signDate);
        addMissingWarning(warnings, "出租方", row.get("出租方"));
        addMissingWarning(warnings, "承租方", row.get("承租方"));
        addMissingWarning(warnings, "租赁期限", row.get("租赁开始时间"));
        addMissingWarning(warnings, "房屋租金", rentGroups.isEmpty() ? "" : rentGroups.get(0).rentAmount);
        String parseErrorMsg = warnings.isEmpty() ? "" : String.join("；", warnings);
        row.put("备注", parseErrorMsg);

        row.put(META_ORIGINAL_FILE_NAME, StrUtil.blankToDefault(fileName, ""));
        row.put(META_ORIGINAL_FILE_URL, StrUtil.blankToDefault(originalFileUrl, ""));
        row.put(META_ORIGINAL_FILE_PATH, StrUtil.blankToDefault(originalFilePath, ""));
        row.put(META_OCR_RAW_TEXT, StrUtil.blankToDefault(rawText, ""));
        row.put(META_PARSE_STATUS, warnings.isEmpty() ? "success" : "partial");
        row.put(META_PARSE_ERROR_MSG, parseErrorMsg);
        return new ParseResult(headers, row, warnings.isEmpty() ? "success" : "partial", parseErrorMsg);
    }

    private String calculateLeaseYears(String startDate, String endDate) {
        if (StrUtil.isBlank(startDate) || StrUtil.isBlank(endDate)) {
            return "";
        }
        try {
            Period period = Period.between(LocalDate.parse(startDate), LocalDate.parse(endDate).plusDays(1));
            List<String> parts = new ArrayList<>();
            if (period.getYears() > 0) {
                parts.add(period.getYears() + "年");
            }
            if (period.getMonths() > 0) {
                parts.add(period.getMonths() + "个月");
            }
            if (period.getDays() > 0 && parts.isEmpty()) {
                parts.add(period.getDays() + "天");
            }
            return String.join("", parts);
        } catch (Exception ignored) {
            return "";
        }
    }

    private String buildRentInfoJson(List<RentGroup> rentGroups) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < rentGroups.size(); i++) {
            RentGroup group = rentGroups.get(i);
            JSONObject item = new JSONObject(true);
            item.set("index", i + 1);
            item.set("year", StrUtil.blankToDefault(group.year, ""));
            item.set("paymentDate", StrUtil.blankToDefault(group.paymentDate, ""));
            item.set("rentAmount", StrUtil.blankToDefault(group.rentAmount, ""));
            array.add(item);
        }
        return JSONUtil.toJsonStr(array);
    }

    private List<String> buildHeaders(int rentPairCount) {
        List<String> headers = new ArrayList<>(HEADERS_BEFORE_RENT);
        for (int i = 1; i <= rentPairCount; i++) {
            headers.add("房屋租金" + i);
            headers.add("租金交纳日期" + i);
        }
        headers.addAll(HEADERS_AFTER_RENT);
        return headers;
    }

    private String extractContractNo(String fileName) {
        String base = StrUtil.blankToDefault(fileName, "");
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        Matcher matcher = CONTRACT_NO_PREFIX.matcher(base);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractDateAfterLabel(String text, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*[：:﹕]?\\s*(" + DATE_REGEX + ")").matcher(text);
        return matcher.find() ? normalizeDate(matcher.group(1)) : "";
    }

    private String extractSignDate(String text) {
        String tail = tail(text, 1800);
        for (String label : Arrays.asList("合同签订日期", "签订日期", "签约日期")) {
            String value = extractDateAfterLabel(tail, label);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
            Matcher before = Pattern.compile("(" + DATE_REGEX + ")\\s*" + Pattern.quote(label)).matcher(tail);
            if (before.find()) {
                return normalizeDate(before.group(1));
            }
        }
        for (String label : Arrays.asList("合同签订日期", "签订日期", "签约日期")) {
            String value = extractDateAfterLabel(text, label);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String extractLessor(String text) {
        String value = extractLabeledValue(head(text, 1400), Arrays.asList("出租方", "甲方"),
                Arrays.asList("以下简称甲方", "承租方", "乙方", "身份证", "联系电话", "电话", "一、", "一.", "房屋状况"), 140);
        return cleanPartyName(value);
    }

    private Party extractLessee(String text) {
        String value = extractLabeledValue(head(text, 1600), Arrays.asList("承租方", "乙方"),
                Arrays.asList("以下简称乙方", "身份证", "联系电话", "电话", "一、", "一.", "房屋状况", "合同签订日期"), 180);
        return new Party(cleanPartyName(value), value);
    }

    private String extractLesseePhone(String text, String idCard) {
        String explicit = extractPhoneAfterLabels(text, Arrays.asList(
                "乙方联系电话", "乙方电话", "承租方联系电话", "承租方电话", "承租人联系电话", "承租人电话"));
        if (StrUtil.isNotBlank(explicit)) {
            return explicit;
        }

        List<String> idCards = findAll(text, ID_CARD);
        if (StrUtil.isNotBlank(idCard) && !idCards.contains(idCard)) {
            idCards.add(idCard);
        }
        String best = "";
        int bestScore = Integer.MIN_VALUE;
        Matcher matcher = MOBILE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (containedByAny(candidate, idCards)) {
                continue;
            }
            int start = matcher.start();
            String window = around(text, start, 220);
            String before = text.substring(Math.max(0, start - 140), start);
            String after = text.substring(start, Math.min(text.length(), start + 60));
            int score = 0;
            if (containsAny(before, "乙方", "承租方", "承租人")) score += 60;
            if (containsAny(before, "甲方", "出租方", "出租人")) score -= 70;
            if (containsAny(window, "乙方", "承租方", "承租人")) score += 20;
            if (containsAny(window, "联系电话", "电话", "手机")) score += 35;
            if (containsAny(window, "联系人")) score += 20;
            if (containsAny(window, "甲方", "出租方")) score -= 40;
            if (containsAny(around(text, start, 80), "甲方联系电话", "出租方联系电话", "甲方电话", "出租方电话")) score -= 80;
            if (containsAny(around(text, start, 80), "乙方联系电话", "承租方联系电话", "乙方电话", "承租方电话")) score += 80;
            if (containsAny(before + after, "乙方签字", "承租方签字", "乙方盖章", "承租方盖章")) score += 70;
            if (start > Math.max(0, text.length() - 1800)) score += 10;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return bestScore >= 0 ? best : "";
    }

    private String extractPhoneAfterLabels(String text, List<String> labels) {
        for (String label : labels) {
            int idx = text.indexOf(label);
            if (idx < 0) {
                continue;
            }
            String window = text.substring(idx, Math.min(text.length(), idx + 140));
            Matcher matcher = MOBILE.matcher(window);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }

    private String extractHouseCondition(String text) {
        Matcher seat = Pattern.compile("房屋[座坐]落于\\s*(.{2,220}?)(?=\\s*(?:[。；;]|二[、.]|租赁期|出租房的用途|$))").matcher(text);
        if (seat.find()) {
            return cleanClause(seat.group(1));
        }
        Matcher matcher = Pattern.compile("甲方出租给乙方的房屋座落于\\s*(.{2,180}?)(?=\\s*(?:二[、.]|租赁期|出租房的用途|$))").matcher(text);
        if (matcher.find()) {
            return cleanClause(matcher.group(1));
        }
        return extractLabeledValue(text, Arrays.asList("一、房屋状况", "房屋状况"),
                Arrays.asList("二、", "租赁期", "出租房的用途"), 180);
    }

    private List<String> extractLeasePeriod(String text) {
        Matcher matcher = Pattern.compile("租赁期自\\s*(" + DATE_REGEX + ")\\s*(?:起)?\\s*(?:至|到)\\s*(" + DATE_REGEX + ")").matcher(text);
        if (matcher.find()) {
            return Arrays.asList(normalizeDate(matcher.group(1)), normalizeDate(matcher.group(2)));
        }
        matcher = Pattern.compile("自\\s*(" + DATE_REGEX + ")\\s*(?:起)?\\s*(?:至|到)\\s*(" + DATE_REGEX + ")\\s*(?:止|日止)?").matcher(text);
        if (matcher.find()) {
            return Arrays.asList(normalizeDate(matcher.group(1)), normalizeDate(matcher.group(2)));
        }
        return new ArrayList<>();
    }

    private String extractLeasePurpose(String text) {
        Matcher matcher = Pattern.compile("出租房的用途约定为\\s*[：:﹕]?\\s*[—\\-]?\\s*(.{1,40}?)(?=\\s*(?:[。；;]|\\d+[、.]|四[、.]|房屋租金|房屋年租金|租金|$))").matcher(text);
        if (matcher.find()) {
            return cleanClause(matcher.group(1)).replaceAll("^[—\\-]+", "").trim();
        }
        matcher = Pattern.compile("租赁用途\\s*[：:﹕]?\\s*(.{1,80}?)(?=\\s*(?:[。；;]|\\d+[、.]|四[、.]|房屋租金|房屋年租金|租金|$))").matcher(text);
        if (matcher.find()) {
            return cleanClause(matcher.group(1)).replaceAll("^[—\\-]+", "").trim();
        }
        return "";
    }

    private List<RentGroup> extractRentGroups(String text, String leaseStartDate) {
        String rentSection = sectionAfter(text, Arrays.asList("四、房屋租金", "四、房屋年租金", "房屋租金", "房屋年租金"),
                Arrays.asList("五、租金交纳", "五、租金缴纳", "五、租金交纳日期", "租金交纳", "租金缴纳", "保证金", "押金", "六、"));
        if (StrUtil.isBlank(rentSection)) {
            rentSection = text;
        }
        String paymentSection = sectionAfter(text, Arrays.asList("五、租金交纳", "五、租金缴纳", "五、租金交纳日期", "租金交纳", "租金缴纳"),
                Arrays.asList("六、", "保证金", "押金", "水费", "电费", "合同期满"));

        List<RentGroup> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("((?:\\d{4}\\s*年[、,，\\s]*){1,8}).{0,70}?(?:每年)?房屋年?租金(?:分别)?为\\s*[￥¥]?\\s*" + MONEY_NUMBER.pattern() + "\\s*元").matcher(rentSection);
        while (matcher.find()) {
            List<Integer> years = extractYears(matcher.group(1));
            String amount = formatMoney(matcher.group(2));
            if (!years.isEmpty() && StrUtil.isNotBlank(amount)) {
                addYearlyRentItems(result, years, amount, paymentSection);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }

        String amount = firstMoney(rentSection);
        if (StrUtil.isBlank(amount)) {
            return result;
        }
        String paymentDate = exactPaymentDate(paymentSection);
        if (StrUtil.isBlank(paymentDate)) {
            paymentDate = exactPaymentDate(text);
        }
        if (StrUtil.isBlank(paymentDate) && (paymentSection.contains("合同签订之前") || paymentSection.contains("合同签订前"))) {
            paymentDate = "合同签订日期前";
        }
        String year = "";
        if (StrUtil.isNotBlank(leaseStartDate) && leaseStartDate.length() >= 4) {
            year = leaseStartDate.substring(0, 4);
        }
        result.add(new RentGroup(year, amount, paymentDate));
        return result;
    }

    private void addYearlyRentItems(List<RentGroup> result, List<Integer> years, String amount, String paymentSection) {
        String yearlyDate = yearlyPaymentDay(paymentSection);
        boolean firstYearBeforeSign = paymentSection.contains("合同签订之前")
                || paymentSection.contains("合同签订前")
                || paymentSection.contains("一次性付清");
        for (int i = 0; i < years.size(); i++) {
            Integer year = years.get(i);
            String value = "";
            if (result.isEmpty() && i == 0 && firstYearBeforeSign) {
                value = "合同签订日期前";
            } else if (StrUtil.isNotBlank(yearlyDate)) {
                value = year + yearlyDate;
            }
            result.add(new RentGroup(String.valueOf(year), amount, value));
        }
    }

    private String yearlyPaymentDay(String text) {
        Matcher yearly = Pattern.compile("每年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?\\s*前").matcher(text);
        if (!yearly.find()) {
            return "";
        }
        return String.format("-%02d-%02d前", Integer.parseInt(yearly.group(1)), Integer.parseInt(yearly.group(2)));
    }

    private String exactPaymentDate(String text) {
        Matcher matcher = Pattern.compile("(" + DATE_REGEX + ")\\s*前").matcher(text);
        return matcher.find() ? normalizeDate(matcher.group(1)) + "前" : "";
    }

    private String extractDeposit(String text) {
        Matcher amountMatcher = Pattern.compile("(?:履约)?保证金.{0,30}?[￥¥]?\\s*(" + MONEY_NUMBER.pattern() + ")\\s*元").matcher(text);
        if (amountMatcher.find()) {
            return formatMoney(amountMatcher.group(1));
        }
        String clause = extractClause(text, Arrays.asList("履约保证金", "保证金", "押金"), 140);
        String amount = firstMoney(clause);
        return StrUtil.isNotBlank(amount) ? amount : clause;
    }

    private String extractUtilityFee(String text, String label) {
        String numeric = extractNumericUtilityFee(text, label);
        if (StrUtil.isNotBlank(numeric)) {
            return numeric;
        }
        String clause = extractClause(text, Arrays.asList(label), 180);
        if (StrUtil.isBlank(clause)) {
            clause = extractClause(text, Arrays.asList("水费", "电费"), 180);
        }
        if (StrUtil.isBlank(clause)) {
            return "";
        }
        if (clause.contains("乙方承担")) {
            return "由乙方承担";
        }
        if (clause.contains("甲方承担")) {
            return "由甲方承担";
        }
        return clause;
    }

    private String extractNumericUtilityFee(String text, String label) {
        String unitPattern = "水费".equals(label)
                ? "(吨|立方米|立方)"
                : "(度|kwh|KWH|千瓦时)";
        Matcher matcher = Pattern.compile(Pattern.quote(label)
                + ".{0,24}?([0-9]+(?:\\.[0-9]+)?)\\s*元\\s*(?:/|每)?\\s*" + unitPattern).matcher(text);
        if (matcher.find()) {
            String unit = matcher.group(2);
            return matcher.group(1) + "元/" + unit;
        }
        matcher = Pattern.compile(Pattern.quote(label)
                + ".{0,24}?([0-9]+(?:\\.[0-9]+)?)\\s*元.{0,4}" + unitPattern).matcher(text);
        if (matcher.find()) {
            String unit = matcher.group(2);
            return matcher.group(1) + "元/" + unit;
        }
        return "";
    }

    private List<Integer> extractYears(String raw) {
        Set<Integer> values = new LinkedHashSet<>();
        Matcher matcher = Pattern.compile("(\\d{4})\\s*年").matcher(raw);
        while (matcher.find()) {
            values.add(Integer.parseInt(matcher.group(1)));
        }
        return new ArrayList<>(values);
    }

    private String firstMoney(String text) {
        Matcher matcher = MONEY_AMOUNT.matcher(text);
        return matcher.find() ? formatMoney(matcher.group(1)) : "";
    }

    private String formatMoney(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        try {
            String numeric = raw.replace("，", ",").replace(",", "").replaceAll("[^0-9.]", "");
            return new BigDecimal(numeric).setScale(2, RoundingMode.HALF_UP).toPlainString() + "元";
        } catch (Exception ignored) {
            return raw.replaceAll("\\s+", "") + (raw.contains("元") ? "" : "元");
        }
    }

    private String extractClause(String text, List<String> labels, int maxLength) {
        for (String label : labels) {
            int idx = text.indexOf(label);
            if (idx < 0) continue;
            int end = text.length();
            for (String delimiter : Arrays.asList("。", "；", ";", "\n", "六、", "七、", "八、")) {
                int next = text.indexOf(delimiter, idx + label.length());
                if (next > idx && next < end) {
                    end = next + (delimiter.length() == 1 && "。；;".contains(delimiter) ? 1 : 0);
                }
            }
            return cleanClause(StrUtil.maxLength(text.substring(idx, Math.min(end, text.length())), maxLength));
        }
        return "";
    }

    private String extractLabeledValue(String text, List<String> labels, List<String> nextLabels, int maxLength) {
        int bestIdx = -1;
        String bestLabel = null;
        for (String label : labels) {
            int idx = text.indexOf(label);
            if (idx >= 0 && (bestIdx < 0 || idx < bestIdx)) {
                bestIdx = idx;
                bestLabel = label;
            }
        }
        if (bestIdx < 0) {
            return "";
        }
        int start = bestIdx + bestLabel.length();
        while (start < text.length() && " ：:﹕".indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        int end = Math.min(text.length(), start + maxLength);
        for (String next : nextLabels) {
            int idx = text.indexOf(next, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return cleanClause(text.substring(start, end));
    }

    private String sectionAfter(String text, List<String> starts, List<String> ends) {
        int startIdx = -1;
        String startLabel = null;
        for (String start : starts) {
            int idx = text.indexOf(start);
            if (idx >= 0 && (startIdx < 0 || idx < startIdx)) {
                startIdx = idx;
                startLabel = start;
            }
        }
        if (startIdx < 0) {
            return "";
        }
        int start = startIdx + startLabel.length();
        int end = text.length();
        for (String marker : ends) {
            int idx = text.indexOf(marker, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return text.substring(start, end);
    }

    private String normalizeDate(String raw) {
        if (StrUtil.isBlank(raw)) {
            return "";
        }
        Matcher chinese = CHINESE_DATE.matcher(raw);
        if (chinese.find()) {
            return formatDate(chinese.group(1), chinese.group(2), chinese.group(3));
        }
        Matcher numeric = NUMERIC_DATE.matcher(raw);
        if (numeric.find()) {
            return formatDate(numeric.group(1), numeric.group(2), numeric.group(3));
        }
        Matcher compact = COMPACT_DATE.matcher(raw);
        if (compact.find()) {
            return formatDate(compact.group(1), compact.group(2), compact.group(3));
        }
        return "";
    }

    private String formatDate(String year, String month, String day) {
        return String.format("%d-%02d-%02d", Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }

    private String normalizeText(String rawText) {
        return StrUtil.blankToDefault(rawText, "")
                .replace('\r', '\n')
                .replace('　', ' ')
                .replaceAll("[_＿]+", "")
                .replaceAll("夸克扫描王|高拍扫描王|扫描全能王", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n+", "\n")
                .trim();
    }

    private String cleanPartyName(String raw) {
        String value = cleanClause(raw);
        value = value.replaceAll("^(出租方|承租方|甲方|乙方)[：:﹕]?", "").trim();
        value = value.replaceAll("[（(]?以下简称[甲乙]方[）)]?.*$", "").trim();
        value = removeFirst(value, ID_CARD);
        value = value.replaceAll("(身份证号?|联系电话|电话).*$", "").trim();
        value = value.replaceAll("[（(]+\\s*$", "").trim();
        return value;
    }

    private String cleanClause(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("[_＿]+", "")
                .trim();
        value = value.replaceAll("^[：:﹕，,、；;。\\s]+", "");
        value = value.replaceAll("[；;，,、\\s]+$", "");
        return value;
    }

    private String removeFirst(String value, Pattern pattern) {
        return StrUtil.isBlank(value) ? "" : pattern.matcher(value).replaceFirst("").trim();
    }

    private String firstMatch(String value, Pattern pattern) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private List<String> findAll(String value, Pattern pattern) {
        List<String> result = new ArrayList<>();
        if (StrUtil.isBlank(value)) {
            return result;
        }
        Matcher matcher = pattern.matcher(value);
        while (matcher.find()) {
            result.add(matcher.group());
        }
        return result;
    }

    private boolean containedByAny(String value, List<String> containers) {
        for (String container : containers) {
            if (StrUtil.isNotBlank(container) && container.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAny(String value, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnitName(String value) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        for (String keyword : UNIT_KEYWORDS) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String head(String value, int length) {
        return value.length() <= length ? value : value.substring(0, length);
    }

    private String tail(String value, int length) {
        return value.length() <= length ? value : value.substring(value.length() - length);
    }

    private String around(String value, int index, int radius) {
        int start = Math.max(0, index - radius);
        int end = Math.min(value.length(), index + radius);
        return value.substring(start, end);
    }

    private void addDistinct(List<String> values, String value) {
        if (StrUtil.isBlank(value) || values.contains(value)) {
            return;
        }
        values.add(value);
    }

    private void addMissingWarning(List<String> warnings, String label, String value) {
        if (StrUtil.isBlank(value)) {
            warnings.add("未识别到" + label);
        }
    }

    private static class Party {
        private final String name;
        private final String rawValue;

        private Party(String name, String rawValue) {
            this.name = name;
            this.rawValue = rawValue;
        }
    }

    private static class RentGroup {
        private final String year;
        private final String rentAmount;
        private final String paymentDate;

        private RentGroup(String year, String rentAmount, String paymentDate) {
            this.year = year;
            this.rentAmount = rentAmount;
            this.paymentDate = paymentDate;
        }
    }

    public static class ParseResult {
        private final List<String> headers;
        private final Map<String, String> row;
        private final String parseStatus;
        private final String parseErrorMsg;

        private ParseResult(List<String> headers, Map<String, String> row, String parseStatus, String parseErrorMsg) {
            this.headers = headers;
            this.row = row;
            this.parseStatus = parseStatus;
            this.parseErrorMsg = parseErrorMsg;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public Map<String, String> getRow() {
            return row;
        }

        public String getParseStatus() {
            return parseStatus;
        }

        public String getParseErrorMsg() {
            return parseErrorMsg;
        }
    }
}
