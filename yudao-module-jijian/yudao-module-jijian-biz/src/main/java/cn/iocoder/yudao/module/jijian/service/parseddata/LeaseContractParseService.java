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
    public static final String META_FIELD_SOURCES = "_fieldSources";

    private static final int DEFAULT_RENT_PAIR_COUNT = 5;
    private static final Pattern CONTRACT_NO_PREFIX = Pattern.compile("^([A-Za-z]+\\d+)");
    private static final Pattern CONTRACT_NO_LABEL = Pattern.compile("(?:合同编号|合同号|编号)\\s*[：:﹕]?\\s*([A-Za-z]{1,5}\\d{6,12})");
    private static final Pattern ID_CARD = Pattern.compile(
            "[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");
    private static final Pattern MOBILE = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern CONTACT_PHONE = Pattern.compile("1[3-9]\\d{9}|0\\d{2,3}\\s*[-—－]{1,2}\\s*\\d{7,8}");
    private static final String DATE_REGEX = "\\d{4}\\s*[年\\-/\\.]\\s*\\d{1,2}\\s*[月\\-/\\.]\\s*\\d{1,2}\\s*[日号]?";
    private static final Pattern CHINESE_DATE = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?");
    private static final Pattern NUMERIC_DATE = Pattern.compile("(\\d{4})\\s*[-/\\.]\\s*(\\d{1,2})\\s*[-/\\.]\\s*(\\d{1,2})");
    private static final Pattern COMPACT_DATE = Pattern.compile("(?<!\\d)(\\d{4})(\\d{2})(\\d{2})(?!\\d)");
    private static final Pattern MONEY_AMOUNT = Pattern.compile("([0-9][0-9,，.]*\\s*元)");
    private static final Pattern MONEY_NUMBER = Pattern.compile("([0-9]+(?:[,，][0-9]{3})*(?:\\.[0-9]{1,2})?|[0-9]+)");
    private static final String CURRENCY_PREFIX_REGEX = "[￥¥Yy]?";
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*年");
    private static final List<String> LEASE_TITLE_KEYWORDS = Arrays.asList(
            "房屋租赁合同", "房屋出租合同", "租赁合同", "租赁合同书", "出租合同", "租房合同", "租赁协议", "房屋租赁协议");
    private static final List<String> TABLE_HEADER_KEYWORDS = Arrays.asList(
            "商品名称", "规格", "单位", "数量", "单价", "小计", "员工编号", "部门", "日期", "星期",
            "上班打卡时间", "下班打卡时间", "申请人", "请假类型", "项目名称", "采价点");

    private static final List<String> HEADERS_BEFORE_RENT = Arrays.asList(
            "合同编号", "合同签订日期", "出租方", "承租方", "承租人身份证号", "承租人联系电话",
            "房屋状况", "租赁开始时间", "租赁结束时间", "租赁年份", "租赁用途");
    private static final List<String> HEADERS_AFTER_RENT = Arrays.asList("保证金", "水费", "电费", "备注");
    private static final List<String> UNIT_KEYWORDS = Arrays.asList("公司", "有限公司", "中心", "单位", "合作社", "商行", "经营部", "学校", "支公司");

    public ParseResult parse(String fileName, String rawText, String originalFileUrl, String originalFilePath) {
        String text = normalizeText(rawText);
        Map<String, String> row = new LinkedHashMap<>();
        Map<String, String> sources = new LinkedHashMap<>();
        List<String> warnings = new ArrayList<>();

        String contractNo = extractContractNo(fileName, text);
        String signDate = extractSignDate(fileName, text);
        Party lessee = extractLessee(text);
        boolean unitLessee = isUnitName(lessee.name);
        String idCard = unitLessee ? "" : firstMatch(lessee.rawValue, ID_CARD);
        if (!unitLessee && StrUtil.isBlank(idCard)) {
            idCard = firstMatch(text, ID_CARD);
        }
        List<String> leasePeriod = extractLeasePeriod(text);
        String leaseStartDate = leasePeriod.size() > 0 ? leasePeriod.get(0) : "";
        String leaseEndDate = leasePeriod.size() > 1 ? leasePeriod.get(1) : "";
        List<RentGroup> rentGroups = extractRentGroups(text, leaseStartDate, leaseEndDate);

        String lessor = extractLessor(text);
        String lesseeName = cleanPartyName(removeFirst(lessee.name, ID_CARD));
        String lesseePhone = extractLesseePhone(text, idCard);
        String houseCondition = extractHouseCondition(text);
        String leasePurpose = extractLeasePurpose(text);
        String deposit = extractDeposit(text);
        String waterFee = extractUtilityFee(text, "水费");
        String electricityFee = extractUtilityFee(text, "电费");

        row.put("合同编号", contractNo);
        row.put("合同签订日期", signDate);
        row.put("出租方", lessor);
        row.put("承租方", lesseeName);
        row.put("承租人身份证号", StrUtil.blankToDefault(idCard, ""));
        row.put("承租人联系电话", lesseePhone);
        row.put("房屋状况", houseCondition);
        row.put("租赁开始时间", leaseStartDate);
        row.put("租赁结束时间", leaseEndDate);
        row.put("租赁年份", calculateLeaseYears(leaseStartDate, leaseEndDate));
        row.put("租赁用途", leasePurpose);

        int rentPairCount = Math.max(DEFAULT_RENT_PAIR_COUNT, rentGroups.size());
        List<String> headers = buildHeaders(rentPairCount);
        for (int i = 0; i < rentPairCount; i++) {
            row.put("房屋租金" + (i + 1), i < rentGroups.size() ? rentGroups.get(i).rentAmount : "");
            row.put("租金交纳日期" + (i + 1), i < rentGroups.size() ? rentGroups.get(i).paymentDate : "");
        }
        row.put("租金明细JSON", buildRentInfoJson(rentGroups));

        row.put("保证金", deposit);
        row.put("水费", waterFee);
        row.put("电费", electricityFee);

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
        putSource(sources, "合同编号", sourceAround(text, contractNo));
        putSource(sources, "合同签订日期", sourceAround(text, signDate));
        putSource(sources, "出租方", sourceAround(text, lessor));
        putSource(sources, "承租方", sourceAround(text, lesseeName));
        putSource(sources, "承租人身份证号", sourceAround(text, idCard));
        putSource(sources, "承租人联系电话", sourceAround(text, lesseePhone));
        putSource(sources, "房屋状况", sourceAround(text, houseCondition));
        putSource(sources, "租赁期限", sourceAround(text, leaseStartDate));
        putSource(sources, "租赁用途", sourceAround(text, leasePurpose));
        putSource(sources, "保证金", sourceAround(text, deposit));
        putSource(sources, "水费", sourceAround(text, waterFee));
        putSource(sources, "电费", sourceAround(text, electricityFee));
        row.put(META_FIELD_SOURCES, JSONUtil.toJsonStr(sources));
        return new ParseResult(headers, row, warnings.isEmpty() ? "success" : "partial", parseErrorMsg);
    }

    public static int scoreLeaseContract(String fileName, String rawText, List<String> headers) {
        String name = StrUtil.blankToDefault(fileName, "");
        String text = normalizeForSignal(rawText);
        if (hasClearTableHeaders(headers, text)) {
            return 0;
        }
        int score = 0;
        if (containsAnyStatic(name + " " + text, LEASE_TITLE_KEYWORDS)) score += 4;
        if (containsAnyStatic(name, Arrays.asList("出租合同", "租赁合同", "房屋租赁", "租房合同"))) score += 3;
        if (containsAnyStatic(text, Arrays.asList("出租方", "承租方"))) score += 2;
        if (containsAnyStatic(text, Arrays.asList("甲方")) && containsAnyStatic(text, Arrays.asList("乙方"))) score += 2;
        if (containsAnyStatic(text, Arrays.asList("租赁期限", "租赁期", "租期", "租赁期自"))) score += 2;
        if (containsAnyStatic(text, Arrays.asList("房屋租金", "年租金", "月租金", "租金", "租赁费"))) score += 2;
        if (containsAnyStatic(text, Arrays.asList("租金交纳", "租金缴纳", "一次性付清"))) score += 1;
        if (containsAnyStatic(text, Arrays.asList("保证金", "押金", "履约保证金"))) score += 1;
        if (containsAnyStatic(text, Arrays.asList("签订日期", "合同签订日期", "签约日期", "签署日期"))) score += 1;
        if (containsAnyStatic(text, Arrays.asList("房屋座落", "房屋坐落", "坐落于", "座落于", "房屋状况"))) score += 1;
        return score;
    }

    public static boolean isLikelyLeaseContract(String fileName, String rawText, List<String> headers) {
        return scoreLeaseContract(fileName, rawText, headers) >= 7;
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

    private String extractContractNo(String fileName, String text) {
        String base = StrUtil.blankToDefault(fileName, "");
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) base = base.substring(slash + 1);
        Matcher matcher = CONTRACT_NO_PREFIX.matcher(base);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = CONTRACT_NO_LABEL.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractDateAfterLabel(String text, String label) {
        Matcher matcher = Pattern.compile(Pattern.quote(label) + "\\s*[：:﹕]?\\s*(" + DATE_REGEX + ")").matcher(text);
        return matcher.find() ? normalizeDate(matcher.group(1)) : "";
    }

    private String extractSignDate(String fileName, String text) {
        String tail = tail(text, 1800);
        for (String label : Arrays.asList("合同签订日期", "签订日期", "签约日期", "签署日期", "签订时间")) {
            String value = extractDateAfterLabel(tail, label);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
            Matcher before = Pattern.compile("(" + DATE_REGEX + ")\\s*" + Pattern.quote(label)).matcher(tail);
            if (before.find()) {
                return normalizeDate(before.group(1));
            }
        }
        Matcher tailDate = Pattern.compile("(?:甲方|乙方|出租方|承租方|签字|盖章).{0,260}?(" + DATE_REGEX + ")").matcher(tail);
        String lastTailDate = "";
        while (tailDate.find()) {
            lastTailDate = normalizeDate(tailDate.group(1));
        }
        if (StrUtil.isNotBlank(lastTailDate)) {
            return lastTailDate;
        }
        for (String label : Arrays.asList("合同签订日期", "签订日期", "签约日期", "签署日期", "签订时间")) {
            String value = extractDateAfterLabel(text, label);
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        String fromFileName = normalizeDate(StrUtil.blankToDefault(fileName, ""));
        if (StrUtil.isNotBlank(fromFileName)) {
            return fromFileName;
        }
        return "";
    }

    private String extractLessor(String text) {
        String value = extractLabeledValue(head(text, 1800), Arrays.asList("出租方", "出租人", "甲方"),
                Arrays.asList("以下简称甲方", "承租方", "乙方", "身份证", "联系电话", "电话", "一、", "一.", "房屋状况"), 140);
        if (StrUtil.isBlank(cleanPartyName(value))) {
            Matcher matcher = Pattern.compile("(?:出租方|出租人|甲方)\\s*[：:﹕]?\\s*（?以下简称甲方）?\\s*_?\\s*(.{2,140}?)(?=\\s*(?:承租方|乙方|为有效|一[、.]|房屋的[座坐]落|房屋状况|$))").matcher(head(text, 1800));
            if (matcher.find()) {
                value = matcher.group(1);
            }
        }
        return cleanPartyName(value);
    }

    private Party extractLessee(String text) {
        String headText = head(text, 2200);
        String value = extractLabeledValue(headText, Arrays.asList("承租方", "承租人", "乙方"),
                Arrays.asList("以下简称乙方", "身份证", "联系电话", "电话", "一、", "一.", "房屋状况", "合同签订日期"), 180);
        if (StrUtil.isBlank(cleanPartyName(value))) {
            Matcher matcher = Pattern.compile("(?:承租方|承租人|乙方)\\s*[：:﹕]?\\s*（?以下简称乙方）?\\s*_?\\s*(.{2,140}?)(?=\\s*(?:为有效|甲方将|一[、.]|房屋的坐落|房屋状况|$))").matcher(headText);
            if (matcher.find()) {
                value = matcher.group(1);
            }
        }
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
        Matcher matcher = CONTACT_PHONE.matcher(text);
        while (matcher.find()) {
            String candidate = normalizePhone(matcher.group());
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
            Matcher matcher = CONTACT_PHONE.matcher(window);
            if (matcher.find()) {
                return normalizePhone(matcher.group());
            }
        }
        return "";
    }

    private String extractHouseCondition(String text) {
        Matcher seat = Pattern.compile("房屋[座坐]落(?:于|在)?\\s*(.{2,260}?)(?=\\s*(?:[。；;]|二[、.]|租赁期|租赁期限|出租房的用途|$))").matcher(text);
        if (seat.find()) {
            return cleanClause(seat.group(1));
        }
        Matcher ownedSeat = Pattern.compile("甲方将(?:其)?(?:合法拥有的)?[座坐]落(?:于|在)?\\s*_?\\s*(.{2,220}?)(?=\\s*_?\\s*出租给乙方)").matcher(text);
        if (ownedSeat.find()) {
            return cleanClause(ownedSeat.group(1));
        }
        Matcher matcher = Pattern.compile("甲方出租给乙方的房屋[座坐]落(?:于|在)?\\s*(.{2,220}?)(?=\\s*(?:二[、.]|租赁期|租赁期限|出租房的用途|$))").matcher(text);
        if (matcher.find()) {
            return cleanClause(matcher.group(1));
        }
        return extractLabeledValue(text, Arrays.asList("一、房屋状况", "房屋状况"),
                Arrays.asList("二、", "租赁期", "出租房的用途"), 180);
    }

    private List<String> extractLeasePeriod(String text) {
        Matcher matcher = Pattern.compile("租赁(?:期限|期|时间)?(?:为|自)?\\s*(" + DATE_REGEX + ")\\s*(?:起)?\\s*(?:至|到|-|—)\\s*(" + DATE_REGEX + ")").matcher(text);
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

    private List<RentGroup> extractRentGroups(String text, String leaseStartDate, String leaseEndDate) {
        String rentSection = sectionAfter(text, Arrays.asList("四、房屋租金", "四、房屋年租金", "四、租金及支付方式",
                        "租金及支付方式", "租金支付方式", "房屋租金", "房屋年租金"),
                Arrays.asList("五、租金交纳", "五、租金缴纳", "五、租金交纳日期", "租金交纳", "租金缴纳", "保证金", "押金", "六、"));
        if (StrUtil.isBlank(rentSection)) {
            rentSection = text;
        }
        String paymentSection = sectionAfter(text, Arrays.asList("五、租金交纳", "五、租金缴纳", "五、租金交纳日期", "租金交纳", "租金缴纳"),
                Arrays.asList("六、", "保证金", "押金", "水费", "电费", "合同期满"));
        if (StrUtil.isBlank(paymentSection)) {
            paymentSection = sectionAfter(text, Arrays.asList("四、租金及支付方式", "租金及支付方式", "租金支付方式", "支付方式"),
                    Arrays.asList("五、", "六、", "保证金", "押金", "水费", "电费", "其他费用"));
        }
        if (StrUtil.isBlank(paymentSection)) {
            paymentSection = text;
        }

        List<RentGroup> result = new ArrayList<>();
        Matcher matcher = Pattern.compile("((?:20\\d{2}\\s*年[、,，和及\\s]*){1,12}).{0,90}?(?:每年)?(?:房屋)?年?租金(?:分别)?为\\s*_?\\s*" + CURRENCY_PREFIX_REGEX + "\\s*" + MONEY_NUMBER.pattern() + "\\s*元").matcher(rentSection);
        while (matcher.find()) {
            List<Integer> years = extractYears(matcher.group(1));
            String amount = formatMoney(matcher.group(2));
            if (!years.isEmpty() && StrUtil.isNotBlank(amount)) {
                addYearlyRentItems(result, years, amount, paymentSection);
            }
        }
        matcher = Pattern.compile("(20\\d{2})\\s*年\\s*(?:至|到|-|—)\\s*(20\\d{2})\\s*年.{0,80}?(?:每年)?(?:房屋)?年?租金(?:为|分别为)?\\s*_?\\s*" + CURRENCY_PREFIX_REGEX + "\\s*" + MONEY_NUMBER.pattern() + "\\s*元").matcher(rentSection);
        while (matcher.find()) {
            int startYear = Integer.parseInt(matcher.group(1));
            int endYear = Integer.parseInt(matcher.group(2));
            String amount = formatMoney(matcher.group(3));
            if (endYear >= startYear && endYear - startYear <= 30 && StrUtil.isNotBlank(amount)) {
                addYearlyRentItems(result, rangeYears(startYear, endYear), amount, paymentSection);
            }
        }
        matcher = Pattern.compile("第([一二三四五六七八九十]+)年(?:至|到|-|—)第([一二三四五六七八九十]+)年.{0,60}?(?:每年)?(?:房屋)?年?租金(?:为)?\\s*_?\\s*" + CURRENCY_PREFIX_REGEX + "\\s*" + MONEY_NUMBER.pattern() + "\\s*元").matcher(rentSection);
        while (matcher.find()) {
            List<Integer> years = ordinalYears(leaseStartDate, matcher.group(1), matcher.group(2));
            String amount = formatMoney(matcher.group(3));
            if (!years.isEmpty() && StrUtil.isNotBlank(amount)) {
                addYearlyRentItems(result, years, amount, paymentSection);
            }
        }
        matcher = Pattern.compile("第([一二三四五六七八九十]+)年.{0,40}?(?:房屋)?年?租金(?:为)?\\s*_?\\s*" + CURRENCY_PREFIX_REGEX + "\\s*" + MONEY_NUMBER.pattern() + "\\s*元").matcher(rentSection);
        while (matcher.find()) {
            List<Integer> years = ordinalYears(leaseStartDate, matcher.group(1), matcher.group(1));
            String amount = formatMoney(matcher.group(2));
            if (!years.isEmpty() && StrUtil.isNotBlank(amount)) {
                addYearlyRentItems(result, years, amount, paymentSection);
            }
        }
        result = dedupeRentGroups(result);
        if (!result.isEmpty()) {
            if (result.size() == 1 && containsAny(text, "每年一付", "以后年度", "以后年份", "后续年份", "每年")) {
                List<Integer> expandedYears = yearsFromPaymentSchedule(text, paymentSection, leaseStartDate);
                if (expandedYears.size() <= 1) {
                    expandedYears = leaseYears(leaseStartDate, leaseEndDate);
                }
                if (expandedYears.size() <= 1) {
                    expandedYears = leaseYearsByDuration(text, leaseStartDate);
                }
                if (expandedYears.size() > 1) {
                    List<RentGroup> expanded = new ArrayList<>();
                    addYearlyRentItems(expanded, expandedYears, result.get(0).rentAmount, paymentSection);
                    return dedupeRentGroups(expanded);
                }
            }
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
        List<Integer> leaseYears = yearsFromPaymentSchedule(text, paymentSection, leaseStartDate);
        if (leaseYears.size() <= 1) {
            leaseYears = leaseYears(leaseStartDate, leaseEndDate);
        }
        if (leaseYears.size() <= 1) {
            leaseYears = leaseYearsByDuration(text, leaseStartDate);
        }
        if (leaseYears.size() > 1 && containsAny(rentSection, "每年", "每年的租金", "每年租金", "每年房屋年租金", "年租金为")
                && containsAny(text, "每年一付", "以后年度", "以后年份", "后续年份", "每年")) {
            addYearlyRentItems(result, leaseYears, amount, paymentSection);
            return dedupeRentGroups(result);
        }
        String year = StrUtil.isNotBlank(leaseStartDate) && leaseStartDate.length() >= 4 ? leaseStartDate.substring(0, 4) : "";
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
            } else if (StrUtil.isNotBlank(paymentDateByYear(paymentSection, year))) {
                value = paymentDateByYear(paymentSection, year);
            } else if (StrUtil.isNotBlank(yearlyDate)) {
                value = year + yearlyDate;
            }
            result.add(new RentGroup(String.valueOf(year), amount, value));
        }
    }

    private String paymentDateByYear(String text, Integer year) {
        if (year == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(Pattern.quote(String.valueOf(year)) + "\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?\\s*前?").matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return String.format("%d-%02d-%02d前", year, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    private String yearlyPaymentDay(String text) {
        Matcher yearly = Pattern.compile("(?:每年|后续年份|以后年度|以后年份).{0,20}?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?\\s*前").matcher(text);
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
        Matcher amountMatcher = Pattern.compile("(?:履约)?保证金.{0,30}?_?\\s*" + CURRENCY_PREFIX_REGEX + "\\s*(" + MONEY_NUMBER.pattern() + ")\\s*元").matcher(text);
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
                ? "(吨|立方米|立方|年|月)"
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
        Matcher matcher = YEAR.matcher(raw);
        while (matcher.find()) {
            values.add(Integer.parseInt(matcher.group(1)));
        }
        return new ArrayList<>(values);
    }

    private String firstMoney(String text) {
        Matcher matcher = MONEY_AMOUNT.matcher(text);
        return matcher.find() ? formatMoney(matcher.group(1)) : "";
    }

    private String normalizePhone(String raw) {
        return StrUtil.blankToDefault(raw, "").replaceAll("\\s+", "").replaceAll("[-—－]{2,}", "-");
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
        String found = "";
        while (compact.find()) {
            found = formatDate(compact.group(1), compact.group(2), compact.group(3));
        }
        if (StrUtil.isNotBlank(found)) {
            return found;
        }
        return "";
    }

    private List<Integer> rangeYears(int startYear, int endYear) {
        List<Integer> years = new ArrayList<>();
        for (int year = startYear; year <= endYear; year++) {
            years.add(year);
        }
        return years;
    }

    private List<Integer> leaseYears(String leaseStartDate, String leaseEndDate) {
        if (StrUtil.isBlank(leaseStartDate) || leaseStartDate.length() < 4
                || StrUtil.isBlank(leaseEndDate) || leaseEndDate.length() < 4) {
            return new ArrayList<>();
        }
        int startYear;
        int endYear;
        try {
            startYear = Integer.parseInt(leaseStartDate.substring(0, 4));
            endYear = Integer.parseInt(leaseEndDate.substring(0, 4));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        if (endYear < startYear || endYear - startYear > 30) {
            return new ArrayList<>();
        }
        return rangeYears(startYear, Math.max(startYear, endYear - 1));
    }

    private List<Integer> yearsFromPaymentSchedule(String text, String paymentSection, String leaseStartDate) {
        List<Integer> paymentYears = extractYears(paymentSection);
        if (paymentYears.isEmpty()) {
            return new ArrayList<>();
        }
        String startDate = leaseStartDate;
        if (StrUtil.isBlank(startDate) || startDate.length() < 4) {
            Matcher startMatcher = Pattern.compile("自\\s*(" + DATE_REGEX + ")").matcher(text);
            startDate = startMatcher.find() ? normalizeDate(startMatcher.group(1)) : "";
        }
        if (StrUtil.isBlank(startDate) || startDate.length() < 4) {
            return paymentYears;
        }
        List<Integer> years = new ArrayList<>();
        try {
            years.add(Integer.parseInt(startDate.substring(0, 4)));
        } catch (Exception ignored) {
            return paymentYears;
        }
        for (Integer year : paymentYears) {
            if (!years.contains(year)) {
                years.add(year);
            }
        }
        return years;
    }

    private List<Integer> leaseYearsByDuration(String text, String leaseStartDate) {
        String startDate = leaseStartDate;
        if (StrUtil.isBlank(startDate) || startDate.length() < 4) {
            Matcher startMatcher = Pattern.compile("自\\s*(" + DATE_REGEX + ")").matcher(text);
            startDate = startMatcher.find() ? normalizeDate(startMatcher.group(1)) : "";
        }
        if (StrUtil.isBlank(startDate) || startDate.length() < 4) {
            return new ArrayList<>();
        }
        Matcher matcher = Pattern.compile("租赁期(?:限)?为\\s*([一二三四五六七八九十\\d]{1,3})\\s*年").matcher(text);
        if (!matcher.find()) {
            return new ArrayList<>();
        }
        int years = chineseOrdinal(matcher.group(1));
        if (years <= 1 || years > 30) {
            return new ArrayList<>();
        }
        int startYear;
        try {
            startYear = Integer.parseInt(startDate.substring(0, 4));
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return rangeYears(startYear, startYear + years - 1);
    }

    private List<Integer> ordinalYears(String leaseStartDate, String startOrdinal, String endOrdinal) {
        if (StrUtil.isBlank(leaseStartDate) || leaseStartDate.length() < 4) {
            return new ArrayList<>();
        }
        int baseYear = Integer.parseInt(leaseStartDate.substring(0, 4));
        int start = chineseOrdinal(startOrdinal);
        int end = chineseOrdinal(endOrdinal);
        if (start <= 0 || end < start || end - start > 30) {
            return new ArrayList<>();
        }
        return rangeYears(baseYear + start - 1, baseYear + end - 1);
    }

    private int chineseOrdinal(String value) {
        if (StrUtil.isBlank(value)) {
            return 0;
        }
        int result = 0;
        String v = value.trim();
        if (v.matches("\\d+")) {
            try {
                return Integer.parseInt(v);
            } catch (Exception ignored) {
                return 0;
            }
        }
        if ("十".equals(v)) return 10;
        int tenIdx = v.indexOf('十');
        if (tenIdx >= 0) {
            String left = v.substring(0, tenIdx);
            String right = v.substring(tenIdx + 1);
            result += StrUtil.isBlank(left) ? 10 : chineseDigit(left) * 10;
            result += chineseDigit(right);
            return result;
        }
        return chineseDigit(v);
    }

    private int chineseDigit(String value) {
        if (StrUtil.isBlank(value)) return 0;
        switch (value.charAt(0)) {
            case '一': return 1;
            case '二': case '两': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return 0;
        }
    }

    private List<RentGroup> dedupeRentGroups(List<RentGroup> rentGroups) {
        Map<String, RentGroup> byYear = new LinkedHashMap<>();
        for (RentGroup group : rentGroups) {
            String key = StrUtil.blankToDefault(group.year, "idx-" + byYear.size());
            byYear.putIfAbsent(key, group);
        }
        return new ArrayList<>(byYear.values());
    }

    private String normalizeText(String rawText) {
        String value = StrUtil.blankToDefault(rawText, "")
                .replace('\r', '\n')
                .replace('　', ' ')
                .replace('坐', '座')
                .replaceAll("甲\\s+方", "甲方")
                .replaceAll("乙\\s+方", "乙方")
                .replaceAll("出\\s*租\\s*方", "出租方")
                .replaceAll("承\\s*租\\s*方", "承租方")
                .replaceAll("租\\s*赁", "租赁")
                .replaceAll("金\\s*额", "金额")
                .replaceAll("[_＿]+", "")
                .replaceAll("夸克扫描王|高拍扫描王|扫描全能王", "")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])", "")
                .replaceAll("\\n{2,}", "\n")
                .trim();
        return value;
    }

    private static String normalizeForSignal(String rawText) {
        return StrUtil.blankToDefault(rawText, "")
                .replace('坐', '座')
                .replaceAll("甲\\s+方", "甲方")
                .replaceAll("乙\\s+方", "乙方")
                .replaceAll("租\\s*赁", "租赁")
                .replaceAll("\\s+", "");
    }

    private static boolean hasClearTableHeaders(List<String> headers, String text) {
        int hits = 0;
        if (headers != null) {
            for (String header : headers) {
                if (StrUtil.isBlank(header)) continue;
                for (String keyword : TABLE_HEADER_KEYWORDS) {
                    if (header.contains(keyword)) {
                        hits++;
                        break;
                    }
                }
            }
        }
        if (hits >= 4) {
            return true;
        }
        int textHits = 0;
        for (String keyword : TABLE_HEADER_KEYWORDS) {
            if (text.contains(keyword)) {
                textHits++;
            }
        }
        return textHits >= 6 && !containsAnyStatic(text, LEASE_TITLE_KEYWORDS);
    }

    private static boolean containsAnyStatic(String value, List<String> keywords) {
        if (StrUtil.isBlank(value)) {
            return false;
        }
        for (String keyword : keywords) {
            if (value.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void putSource(Map<String, String> sources, String field, String snippet) {
        if (StrUtil.isNotBlank(snippet)) {
            sources.put(field, snippet);
        }
    }

    private String sourceAround(String text, String value) {
        if (StrUtil.isBlank(text) || StrUtil.isBlank(value)) {
            return "";
        }
        String needle = value.replace("元", "");
        int idx = text.indexOf(value);
        if (idx < 0 && StrUtil.isNotBlank(needle)) {
            idx = text.indexOf(needle);
        }
        if (idx < 0) {
            return "";
        }
        return cleanClause(around(text, idx, 80));
    }

    private String formatDate(String year, String month, String day) {
        try {
            return String.format("%d-%02d-%02d",
                    Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String cleanPartyName(String raw) {
        String value = cleanClause(raw);
        value = value.replaceAll("^(出租方|承租方|甲方|乙方)[：:﹕]?", "").trim();
        value = value.replaceAll("[（(]?以下简称[甲乙]方[）)]?.*$", "").trim();
        value = removeFirst(value, ID_CARD);
        value = value.replaceAll("(身份证号?|联系电话|电话).*$", "").trim();
        value = value.replaceAll("\\s*(甲方|乙方|出租方|承租方)$", "").trim();
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
