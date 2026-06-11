package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 纪检表单类型自动识别服务。
 *
 * <p>识别依据（优先级从高到低）：
 * <ol>
 *   <li>表头关键词命中率（权重最高）</li>
 *   <li>Sheet 名称关键词</li>
 *   <li>文件名关键词</li>
 * </ol>
 *
 * <p>返回置信度 0.0~1.0，低于 {@link #LOW_CONFIDENCE_THRESHOLD} 时建议用户手工确认。
 */
@Slf4j
@Service
public class JijianFormTypeAutoDetectService {

    /** 低置信度阈值，低于此值需前端提示用户选择 */
    public static final double LOW_CONFIDENCE_THRESHOLD = 0.7;

    // ── 9 类表单的关键词规则 ──────────────────────────────────────────────

    /** 每类表单的关键词列表（表头字段名或业务关键词），命中越多置信度越高 */
    private static final Map<String, FormTypeRule> RULES = buildRules();

    /** 食堂供应强匹配字段组（每组任一别名命中即算该字段命中） */
    private static final List<List<String>> CANTEEN_STRONG_FIELDS = Arrays.asList(
            Arrays.asList("商品名称", "品名", "物品名称", "项目名称"),
            Arrays.asList("规格"),
            Arrays.asList("单位"),
            Arrays.asList("数量"),
            Arrays.asList("单价"),
            Arrays.asList("小计"),
            Arrays.asList("备注"));

    /** 食堂供应强匹配时直接给出的置信度（高于低置信度阈值，免去手工确认） */
    private static final double CANTEEN_STRONG_CONFIDENCE = 0.95;

    // ── 公共 API ────────────────────────────────────────────────────────

    /**
     * 根据文件名、Sheet 名、表头列表自动识别表单类型。
     *
     * @param fileName  原始文件名
     * @param sheetName sheet 名称（Excel 第一个 sheet，CSV/OCR 传 null）
     * @param headers   表头字段列表
     * @return 识别结果；若完全无法识别返回 formType=null confidence=0
     */
    public DetectResult detect(String fileName, String sheetName, List<String> headers) {
        List<CandidateScore> scores = new ArrayList<>();

        for (Map.Entry<String, FormTypeRule> e : RULES.entrySet()) {
            String formType = e.getKey();
            FormTypeRule rule = e.getValue();
            double score = computeScore(fileName, sheetName, headers, rule);
            scores.add(new CandidateScore(formType, rule.displayName, score, matchedHeaders(headers, rule)));
        }

        // 食堂供应强匹配：命中 7 个核心字段中至少 5 个，或同时命中 商品名称+数量+单价+小计
        if (isCanteenStrongMatch(headers)) {
            for (int i = 0; i < scores.size(); i++) {
                CandidateScore s = scores.get(i);
                if (FormTypeConstants.CANTEEN.equals(s.formType) && s.confidence < CANTEEN_STRONG_CONFIDENCE) {
                    scores.set(i, new CandidateScore(s.formType, s.displayName,
                            CANTEEN_STRONG_CONFIDENCE, s.matchedHeaders));
                }
            }
        }

        // 按置信度降序排列
        scores.sort(Comparator.comparingDouble((CandidateScore c) -> c.confidence).reversed());

        if (scores.isEmpty() || scores.get(0).confidence <= 0) {
            return DetectResult.unknown();
        }

        CandidateScore best = scores.get(0);
        List<CandidateScore> candidates = new ArrayList<>();
        for (CandidateScore s : scores) {
            if (s.confidence > 0.1) candidates.add(s);
        }

        log.info("[AutoDetect] fileName={} best={} conf={} matched={}",
                fileName, best.formType, best.confidence, best.matchedHeaders);

        return new DetectResult(
                best.formType,
                best.displayName,
                best.confidence,
                best.matchedHeaders,
                candidates
        );
    }

    // ── 内部计算 ─────────────────────────────────────────────────────────

    private double computeScore(String fileName, String sheetName,
                                List<String> headers, FormTypeRule rule) {
        double score = 0.0;

        // 1. 文件名关键词匹配（权重 0.3）
        if (StrUtil.isNotBlank(fileName)) {
            for (String kw : rule.fileNameKeywords) {
                if (fileName.contains(kw)) {
                    score += 0.3;
                    break;
                }
            }
        }

        // 2. Sheet 名匹配（权重 0.2）
        if (StrUtil.isNotBlank(sheetName)) {
            for (String kw : rule.sheetKeywords) {
                if (sheetName.contains(kw)) {
                    score += 0.2;
                    break;
                }
            }
        }

        // 3. 表头命中率（权重 0.5，按命中的核心字段占比）
        if (headers != null && !headers.isEmpty() && !rule.headerKeywords.isEmpty()) {
            int coreHits = 0;
            for (String kw : rule.coreHeaders) {
                if (headersContain(headers, kw)) coreHits++;
            }
            int generalHits = 0;
            for (String kw : rule.headerKeywords) {
                if (headersContain(headers, kw)) generalHits++;
            }
            // 核心字段权重高：每个核心字段命中 0.15，普通字段命中 0.05
            double headerScore = coreHits * 0.15 + generalHits * 0.05;
            score += Math.min(0.5, headerScore);
        }

        return Math.min(1.0, score);
    }

    /**
     * 食堂供应强匹配判定：
     * 命中 商品名称、规格、单位、数量、单价、小计、备注 中至少 5 个，
     * 或同时命中 商品名称、数量、单价、小计。
     */
    private boolean isCanteenStrongMatch(List<String> headers) {
        if (headers == null || headers.isEmpty()) return false;
        int hits = 0;
        for (List<String> aliases : CANTEEN_STRONG_FIELDS) {
            for (String kw : aliases) {
                if (headersContain(headers, kw)) {
                    hits++;
                    break;
                }
            }
        }
        if (hits >= 5) return true;
        boolean hasName = false;
        for (String kw : CANTEEN_STRONG_FIELDS.get(0)) {
            if (headersContain(headers, kw)) { hasName = true; break; }
        }
        return hasName && headersContain(headers, "数量")
                && headersContain(headers, "单价") && headersContain(headers, "小计");
    }

    private boolean headersContain(List<String> headers, String keyword) {
        for (String h : headers) {
            if (StrUtil.isNotBlank(h) && h.contains(keyword)) return true;
        }
        return false;
    }

    private List<String> matchedHeaders(List<String> headers, FormTypeRule rule) {
        List<String> matched = new ArrayList<>();
        if (headers == null) return matched;
        List<String> allKeywords = new ArrayList<>();
        allKeywords.addAll(rule.coreHeaders);
        allKeywords.addAll(rule.headerKeywords);
        for (String kw : allKeywords) {
            for (String h : headers) {
                if (StrUtil.isNotBlank(h) && h.contains(kw) && !matched.contains(h)) {
                    matched.add(h);
                }
            }
        }
        return matched;
    }

    // ── 规则定义 ─────────────────────────────────────────────────────────

    private static Map<String, FormTypeRule> buildRules() {
        Map<String, FormTypeRule> m = new LinkedHashMap<>();

        m.put(FormTypeConstants.LEAVE_HEALTH, new FormTypeRule(
                "疗休养假",
                Arrays.asList("疗休养", "疗养假", "疗养"),
                Arrays.asList("疗休养", "疗养"),
                Arrays.asList("疗养假开始", "疗养假结束", "休假地点", "工作年限", "参加工作时间"),
                Arrays.asList("申请人", "姓名", "部门", "开始时间", "结束时间", "天数")
        ));

        m.put(FormTypeConstants.LEAVE_PERSONAL, new FormTypeRule(
                "事假记录",
                Arrays.asList("事假", "请假记录"),
                Arrays.asList("事假", "请假"),
                Arrays.asList("请假类型", "请假事由", "是否出义", "假期类型", "事假原因"),
                Arrays.asList("申请人", "姓名", "部门", "开始时间", "结束时间", "天数")
        ));

        m.put(FormTypeConstants.COMPENSATORY, new FormTypeRule(
                "调休记录",
                Arrays.asList("调休", "补休"),
                Arrays.asList("调休", "补休"),
                Arrays.asList("调休开始", "调休结束", "加班开始", "加班结束", "调休时长"),
                Arrays.asList("申请人", "姓名", "部门", "时长", "班次")
        ));

        m.put(FormTypeConstants.BUSINESS_TRIP, new FormTypeRule(
                "出差记录",
                Arrays.asList("出差"),
                Arrays.asList("出差"),
                Arrays.asList("出差事由", "出发地", "目的地", "出差开始", "出差结束", "出差天数"),
                Arrays.asList("申请人", "姓名", "部门", "出差人员", "天数")
        ));

        m.put(FormTypeConstants.ATTENDANCE, new FormTypeRule(
                "考勤日报",
                Arrays.asList("考勤", "打卡", "考勤日报"),
                Arrays.asList("考勤", "打卡", "日报"),
                Arrays.asList("打卡时间", "打卡结果", "打卡地点", "上班备注", "下班备注", "员工编号"),
                Arrays.asList("姓名", "部门", "日期", "星期", "迟到", "早退", "缺卡")
        ));

        m.put(FormTypeConstants.PROPERTY, new FormTypeRule(
                "房产信息",
                Arrays.asList("房产", "不动产"),
                Arrays.asList("房产", "不动产"),
                Arrays.asList("房产地址", "房产名称", "产权信息", "建筑面积", "租赁情况"),
                Arrays.asList("姓名", "地址", "面积", "产权", "备注")
        ));

        m.put(FormTypeConstants.LESSEE, new FormTypeRule(
                "租赁人员",
                Arrays.asList("租赁人员", "承租人", "租赁"),
                Arrays.asList("租赁", "承租"),
                Arrays.asList("是否内部人员", "身份证号", "营业执照", "联系电话"),
                Arrays.asList("姓名", "部门", "地址", "手机")
        ));

        m.put(FormTypeConstants.LEASE_CONTRACT, new FormTypeRule(
                "租赁合同",
                Arrays.asList("租赁合同", "合同"),
                Arrays.asList("租赁合同", "合同"),
                Arrays.asList("合同编号", "合同内容摘要", "租金", "水电费", "支付情况"),
                Arrays.asList("出租方", "承租方", "合同期", "租金")
        ));

        m.put(FormTypeConstants.CANTEEN, new FormTypeRule(
                "食堂供应",
                Arrays.asList("食堂", "供应", "采购", "配送单", "食堂配送单", "供货"),
                Arrays.asList("食堂", "供应", "采价", "配送"),
                Arrays.asList("商品名称", "品名", "规格等级", "采购点", "采价点"),
                Arrays.asList("规格", "单位", "数量", "单价", "小计", "备注", "供应商", "供货商")
        ));

        return m;
    }

    // ── VO 类 ────────────────────────────────────────────────────────────

    public static class DetectResult {
        /** 识别出的 formType 常量，无法识别为 null */
        public final String detectedFormType;
        /** 显示名称（中文） */
        public final String detectedFormName;
        /** 置信度 0.0~1.0 */
        public final double confidence;
        /** 命中的表头字段列表 */
        public final List<String> matchedHeaders;
        /** 所有候选及其置信度（降序） */
        public final List<CandidateScore> candidateTypes;
        /** 是否需要用户手工确认 */
        public final boolean needsConfirmation;

        public DetectResult(String detectedFormType, String detectedFormName,
                            double confidence, List<String> matchedHeaders,
                            List<CandidateScore> candidateTypes) {
            this.detectedFormType = detectedFormType;
            this.detectedFormName = detectedFormName;
            this.confidence = confidence;
            this.matchedHeaders = matchedHeaders;
            this.candidateTypes = candidateTypes;
            this.needsConfirmation = confidence < LOW_CONFIDENCE_THRESHOLD;
        }

        public static DetectResult unknown() {
            return new DetectResult(null, "未识别", 0.0, new ArrayList<>(), new ArrayList<>());
        }
    }

    public static class CandidateScore {
        public final String formType;
        public final String displayName;
        public final double confidence;
        public final List<String> matchedHeaders;

        public CandidateScore(String formType, String displayName,
                              double confidence, List<String> matchedHeaders) {
            this.formType = formType;
            this.displayName = displayName;
            this.confidence = confidence;
            this.matchedHeaders = matchedHeaders;
        }
    }

    private static class FormTypeRule {
        final String displayName;
        final List<String> fileNameKeywords;
        final List<String> sheetKeywords;
        final List<String> coreHeaders;
        final List<String> headerKeywords;

        FormTypeRule(String displayName, List<String> fileNameKeywords,
                     List<String> sheetKeywords, List<String> coreHeaders,
                     List<String> headerKeywords) {
            this.displayName = displayName;
            this.fileNameKeywords = fileNameKeywords;
            this.sheetKeywords = sheetKeywords;
            this.coreHeaders = coreHeaders;
            this.headerKeywords = headerKeywords;
        }
    }
}
