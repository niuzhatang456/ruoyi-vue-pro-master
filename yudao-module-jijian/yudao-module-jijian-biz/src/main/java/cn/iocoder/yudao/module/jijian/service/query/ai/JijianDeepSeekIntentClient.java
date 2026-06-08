package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryMetricEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.framework.JijianProperties;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceQueryService;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceAnalysisService;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceAnalysisService.AnalysisResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@Primary
public class JijianDeepSeekIntentClient implements JijianAiIntentClient {

    private static final String ALL = "ALL";
    private static final Set<String> ALLOWED_KEYS = new HashSet<>(Arrays.asList(
            "formType", "department", "timeRange", "metrics", "needDetail",
            "analysisGoal", "drillDownTarget", "confidence"
    ));
    private static final Set<String> ALLOWED_METRICS = new HashSet<>(Arrays.asList(
            JijianQueryMetricEnum.TOTAL.getValue(),
            JijianQueryMetricEnum.BY_DEPARTMENT.getValue(),
            JijianQueryMetricEnum.BY_ATTENDANCE_STATUS.getValue(),
            JijianQueryMetricEnum.DETAIL_SAMPLE.getValue()
    ));

    @Resource
    private JijianProperties jijianProperties;
    @Resource
    private LocalFallbackAiIntentClient fallback;
    @Resource
    private JijianAttendanceQueryService attendanceQueryService;
    @Resource
    private JijianQueryFieldWhitelist fieldWhitelist;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public IntentResult parseIntent(JijianQueryChatReqVO req) {
        EffectiveDeepSeekConfig cfg = effectiveConfig();
        if (!cfg.enabled || !hasUsableApiKey(cfg.apiKey)) {
            return fallback.parseIntent(req);
        }
        try {
            List<String> allowedDepartments = attendanceQueryService.listDepartments();
            String raw = callDeepSeek(cfg, buildIntentSystemPrompt(), buildIntentUserMessage(req, allowedDepartments));
            JijianAiQueryIntent intent = parseAndValidateIntent(raw, allowedDepartments);
            return new IntentResult(intent, true);
        } catch (Exception e) {
            log.warn("[JijianQueryAI] DeepSeek intent failed, using LOCAL_FALLBACK. reason={}", e.getMessage());
            return fallback.parseIntent(req);
        }
    }

    @Override
    public SummaryResult generateSummary(JijianAiQueryIntent intent, String summaryJson) {
        EffectiveDeepSeekConfig cfg = effectiveConfig();
        if (!cfg.enabled || !cfg.summaryEnabled || !hasUsableApiKey(cfg.apiKey)) {
            return fallback.generateSummary(intent, summaryJson);
        }
        try {
            String safeSummaryJson = toSafeAggregateJson(intent.getFormType(), summaryJson);
            String text = callDeepSeek(cfg, buildSummarySystemPrompt(), buildSummaryUserMessage(intent, safeSummaryJson));
            if (text == null || text.isBlank()) {
                return fallback.generateSummary(intent, summaryJson);
            }
            return new SummaryResult(text.trim(), true);
        } catch (Exception e) {
            log.warn("[JijianQueryAI] DeepSeek summary failed, using local summary. reason={}", e.getMessage());
            return fallback.generateSummary(intent, summaryJson);
        }
    }

    String buildIntentSystemPrompt() {
        return "\u4f60\u662f\u7eaa\u68c0\u67e5\u8be2\u6a21\u5757\u7684\u610f\u56fe\u89e3\u6790\u5668\u3002\u53ea\u8fd4\u56de JSON\uff0c\u4e0d\u8981 Markdown\uff0c\u4e0d\u8981\u89e3\u91ca\u3002\n"
                + "\u53ea\u5141\u8bb8 key: formType, department, timeRange, metrics, needDetail, analysisGoal, drillDownTarget, confidence\u3002\n"
                + "formType \u53ea\u80fd\u4ece allowedFormTypes \u9009\u62e9\uff1bdepartment \u53ea\u80fd\u662f ALL \u6216 allowedDepartments \u4e2d\u7684\u503c\uff1btimeRange \u53ea\u80fd\u4ece allowedTimeRanges \u9009\u62e9\u3002\n"
                + "\u4e0b\u62c9\u6846 hardFormType/hardDepartment/hardTimeRange \u662f\u786c\u9650\u5236\uff0c\u5b58\u5728\u65f6\u5fc5\u987b\u4e25\u683c\u6cbf\u7528\uff0c\u81ea\u7136\u8bed\u8a00\u4e0d\u80fd\u8986\u76d6\u3002\n"
                + "\u4e0d\u5f97\u751f\u6210 SQL\uff0c\u4e0d\u5f97\u8981\u6c42\u6267\u884c SQL\uff0c\u4e0d\u5f97\u7f16\u9020\u8868\u540d\u3001\u5b57\u6bb5\u540d\u3001Mapper\u3001XML \u6216 schema\u3002\n"
                + "\u4e0d\u5f97\u6269\u5927\u7b5b\u9009\u8303\u56f4\uff0c\u4e0d\u5f97\u628a\u591a\u5f20\u8868\u5408\u5e76\u6210\u4e00\u4e2a\u67e5\u8be2\u3002\n"
                + "\u5b57\u6bb5\u767d\u540d\u5355\u4e0d\u5b58\u5728\u7684\u6570\u636e\u4e0d\u5f97\u63a8\u65ad\u3001\u7f16\u9020\u6216\u5206\u6790\u3002\n"
                + "CANTEEN_SUPPLIER \u53ea\u80fd\u5206\u6790\u9879\u76ee\u540d\u79f0\u3001\u89c4\u683c/\u7b49\u7ea7\u3001\u5355\u4f4d\u3001\u4ef7\u683c\u3001\u91c7\u4ef7\u70b9\uff0c\u4e0d\u80fd\u5206\u6790\u8fdd\u89c4\u3001\u6d6a\u8d39\u3001\u5f02\u5e38\u3001\u90e8\u95e8\u3001\u4eba\u5458\u3001\u5ba1\u6279\u3001\u5907\u6ce8\u3001\u4f9b\u5e94\u6b21\u6570\u3002\n"
                + "\u5982\u679c\u95ee\u9898\u6d89\u53ca\u591a\u5f20\u8868\uff0canalysisGoal \u8981\u4fdd\u7559\u8be5\u8fb9\u754c\uff0c\u540e\u7aef\u4f1a\u6309\u5355\u8868\u62d2\u7edd\u865a\u5047\u5408\u5e76\u3002\n"
                + "\u6d89\u53ca\u624b\u673a\u53f7\u3001\u8eab\u4efd\u8bc1\u3001\u8425\u4e1a\u6267\u7167\u65f6\uff0c\u53ea\u80fd\u505a\u5b8c\u6574\u6027\u6216\u662f\u5426\u586b\u5199\u7edf\u8ba1\uff0c\u4e0d\u5f97\u8f93\u51fa\u539f\u59cb\u503c\u3002";
    }

    public String analyzeAttendanceData(AnalysisResult result, String userMessage) {
        EffectiveDeepSeekConfig cfg = effectiveConfig();
        if (!cfg.enabled || !hasUsableApiKey(cfg.apiKey)) {
            return buildLocalAttendanceAnswer(result);
        }
        try {
            String contextJson = buildAttendanceContextJson(result, userMessage);
            String text = callDeepSeek(cfg, buildDataAnalysisSystemPrompt(), contextJson);
            if (text == null || text.isBlank()) {
                return buildLocalAttendanceAnswer(result);
            }
            return text.trim();
        } catch (Exception e) {
            log.warn("[JijianQueryAI] DeepSeek data analysis failed, using local. reason={}", e.getMessage());
            return buildLocalAttendanceAnswer(result);
        }
    }

    private String buildDataAnalysisSystemPrompt() {
        return "你是纪检信息系统的数据分析助手。\n"
                + "你只能基于后端提供的 databaseContext 回答，databaseContext 是唯一数据来源。\n"
                + "不得编造不存在的人员、部门、日期或数字。\n"
                + "如果数据库数据不足，必须明确说明数据不足，不得虚构结论。\n"
                + "不得生成 SQL，不得连接数据库，不得输出手机号、身份证、营业执照原文。\n"
                + "涉及缺勤判断时，必须区分：\n"
                + "  1. 原始缺卡/异常打卡记录\n"
                + "  2. 已被疗休养、事假、出差、调休解释的记录\n"
                + "  3. 无解释的疑似缺勤记录\n"
                + "不得把已解释缺卡人员判断为缺勤。\n"
                + "请用中文输出分析结论，200字以内，重点突出关键数字和部门差异。";
    }

    private String buildAttendanceContextJson(AnalysisResult result, String userMessage) {
        ObjectNode ctx = objectMapper.createObjectNode();
        ctx.put("analysisTask", "考勤缺勤综合分析");
        ctx.put("userQuestion", userMessage == null ? "" : userMessage);
        ctx.put("dataSource", "database");
        ctx.put("timeRange", result.getTimeRangeLabel());
        ctx.put("totalAttendanceRecords", result.getAttendanceList().size());
        ctx.put("rawAnomalyCount", result.getRawAnomalies().size());
        ctx.put("explainedCount", result.getExplainedAnomalies().size());
        ctx.put("unexplainedAbsenceCount", result.getUnexplainedAnomalies().size());

        ObjectNode tableCounts = ctx.putObject("tableCounts");
        tableCounts.put("ATTENDANCE_DAILY", result.getAttendanceList().size());
        tableCounts.put("RECUPERATION_LEAVE", result.getLeaveHealthList().size());
        tableCounts.put("PERSONAL_LEAVE", result.getLeavePersonalList().size());
        tableCounts.put("BUSINESS_TRIP", result.getBusinessTripList().size());
        tableCounts.put("COMPENSATORY_LEAVE", result.getCompensatoryList().size());

        ArrayNode anomalies = ctx.putArray("unexplainedAbsenceItems");
        int limit = Math.min(result.getUnexplainedAnomalies().size(), 30);
        for (int i = 0; i < limit; i++) {
            JijianAttendanceAnalysisService.AnomalyItem item = result.getUnexplainedAnomalies().get(i);
            ObjectNode n = anomalies.addObject();
            n.put("employeeName", item.getEmployeeName() == null ? "" : item.getEmployeeName());
            n.put("department", item.getDepartment() == null ? "" : item.getDepartment());
            n.put("date", item.getAttendanceDate() == null ? "" : item.getAttendanceDate().toString());
            n.put("anomalyType", item.getAnomalyType() == null ? "" : item.getAnomalyType());
        }
        if (result.getUnexplainedAnomalies().size() > 30) {
            ctx.put("unexplainedTruncated", true);
            ctx.put("unexplainedTotal", result.getUnexplainedAnomalies().size());
        }

        ObjectNode explainSources = ctx.putObject("explainSourceDistribution");
        result.getExplainedAnomalies().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        i -> i.getExplainSource() == null ? "其他" : i.getExplainSource(),
                        java.util.stream.Collectors.counting()))
                .forEach(explainSources::put);

        return ctx.toString();
    }

    private String buildLocalAttendanceAnswer(AnalysisResult result) {
        int total = result.getAttendanceList().size();
        int anomaly = result.getRawAnomalies().size();
        int explained = result.getExplainedAnomalies().size();
        int unexplained = result.getUnexplainedAnomalies().size();
        int normal = total - anomaly;
        return String.format(
                "本次分析时间范围：%s。考勤记录共 %d 条，正常出勤 %d 条，"
                        + "原始缺卡/异常 %d 次，其中已解释（疗休养/事假/出差/调休）%d 次，"
                        + "疑似缺勤（无解释）%d 次。请查看下方图表和明细表格获取详情。",
                result.getTimeRangeLabel(), total, normal, anomaly, explained, unexplained
        );
    }

    String buildSummarySystemPrompt() {
        return "\u4f60\u662f\u7eaa\u68c0\u67e5\u8be2\u6a21\u5757\u7684\u4e2d\u6587\u603b\u7ed3\u52a9\u624b\u3002\n"
                + "\u4f60\u53ea\u80fd\u57fa\u4e8e\u540e\u7aef\u63d0\u4f9b\u7684\u5f53\u524d\u5355\u8868 summary \u548c\u5b57\u6bb5\u767d\u540d\u5355\u56de\u7b54\u3002\n"
                + "\u4e0d\u5f97\u751f\u6210 SQL\uff0c\u4e0d\u5f97\u6267\u884c SQL\uff0c\u4e0d\u5f97\u7f16\u9020 summary \u4e2d\u4e0d\u5b58\u5728\u7684\u6570\u636e\u3002\n"
                + "\u4e0d\u5f97\u6269\u5927\u7b5b\u9009\u8303\u56f4\uff0c\u4e0d\u5f97\u4f7f\u7528\u5f53\u524d summary \u4e4b\u5916\u7684\u660e\u7ec6\u3001\u8868\u7ed3\u6784\u3001Mapper XML \u6216 SQL\u3002\n"
                + "\u5b57\u6bb5\u767d\u540d\u5355\u4e0d\u5b58\u5728\u7684\u5185\u5bb9\u5fc5\u987b\u660e\u786e\u62d2\u7edd\u865a\u5047\u5206\u6790\u3002\n"
                + "\u56de\u7b54\u8981\u5b8c\u6574\u5217\u51fa summary \u4e2d\u7684\u6bcf\u4e2a\u7ef4\u5ea6\u3001\u6bcf\u4e2a\u5206\u7ec4\u3001\u6570\u91cf\u3001\u91d1\u989d\u3001\u5360\u6bd4\u3001\u6bd4\u4f8b\u548c\u6392\u5e8f\uff1b\u4e0d\u8981\u7528\u201c\u7b49\u7b49\u201d\u201c\u5176\u4ed6\u201d\u201c\u5927\u6982\u201d\u201c\u53ef\u80fd\u201d\u4ee3\u66ff\u6570\u636e\u3002\n"
                + "CANTEEN_SUPPLIER \u53ea\u80fd\u57fa\u4e8e\u9879\u76ee\u540d\u79f0\u3001\u89c4\u683c/\u7b49\u7ea7\u3001\u5355\u4f4d\u3001\u4ef7\u683c\u3001\u91c7\u4ef7\u70b9\u53ca\u4ef7\u683c\u5dee\u5f02\u6307\u6807\u5206\u6790\uff0c\u9047\u5230\u90e8\u95e8\u3001\u6d6a\u8d39\u3001\u8fdd\u89c4\u3001\u5f02\u5e38\u3001\u4eba\u5458\u3001\u5ba1\u6279\u3001\u5907\u6ce8\u3001\u4f9b\u5e94\u6b21\u6570\u5fc5\u987b\u62d2\u7edd\u865a\u5047\u5206\u6790\u3002\n"
                + "\u654f\u611f\u5b57\u6bb5\u53ea\u80fd\u505a\u5b8c\u6574\u6027\u7edf\u8ba1\uff0c\u4e0d\u5f97\u8f93\u51fa\u624b\u673a\u53f7\u3001\u8eab\u4efd\u8bc1\u3001\u8425\u4e1a\u6267\u7167\u539f\u6587\u3002";
    }

    private String buildIntentUserMessage(JijianQueryChatReqVO req, List<String> allowedDepartments) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("message", req.getMessage() == null ? "" : req.getMessage());
        payload.put("hardFormType", blankToNull(req.getFormType()));
        payload.put("hardDepartment", blankToNull(req.getDepartment()));
        payload.put("hardTimeRange", blankToNull(req.getTimeRange()));

        ArrayNode formTypes = payload.putArray("allowedFormTypes");
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                formTypes.add(e.getValue());
            }
        }

        ArrayNode departments = payload.putArray("allowedDepartments");
        departments.add(ALL);
        for (String department : allowedDepartments) {
            if (department != null && !department.isBlank()) {
                departments.add(department);
            }
        }

        ArrayNode timeRanges = payload.putArray("allowedTimeRanges");
        for (JijianQueryTimeRangeEnum value : JijianQueryTimeRangeEnum.values()) {
            timeRanges.add(value.getValue());
        }

        ObjectNode whitelist = payload.putObject("fieldWhitelist");
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                whitelist.set(e.getValue(), readTreeQuiet(fieldWhitelist.describe(e.getValue())));
            }
        }

        ArrayNode history = payload.putArray("recentHistory");
        if (req.getHistory() != null) {
            int max = Math.max(0, effectiveConfig().maxHistoryRounds) * 2;
            int start = Math.max(0, req.getHistory().size() - max);
            for (int i = start; i < req.getHistory().size(); i++) {
                JijianQueryChatReqVO.ChatHistoryItem item = req.getHistory().get(i);
                if (item != null && item.getContent() != null && !item.getContent().isBlank()) {
                    ObjectNode entry = history.addObject();
                    entry.put("role", item.getRole());
                    entry.put("content", limit(item.getContent(), 300));
                }
            }
        }
        return payload.toString();
    }

    private String buildSummaryUserMessage(JijianAiQueryIntent intent, String safeSummaryJson) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("formType", intent.getFormType());
        payload.put("department", intent.getDepartment());
        payload.put("timeRange", intent.getTimeRange());
        payload.put("analysisGoal", intent.getAnalysisGoal());
        payload.set("fieldWhitelist", readTreeQuiet(fieldWhitelist.describe(intent.getFormType())));
        payload.set("summary", readTreeQuiet(safeSummaryJson));
        return payload.toString();
    }

    private JijianAiQueryIntent parseAndValidateIntent(String rawJson, List<String> allowedDepartments) throws Exception {
        JsonNode node = objectMapper.readTree(stripCodeFence(rawJson));
        if (!node.isObject()) {
            throw new IllegalArgumentException("intent is not a JSON object");
        }
        node.fieldNames().forEachRemaining(key -> {
            if (!ALLOWED_KEYS.contains(key)) {
                throw new IllegalArgumentException("disallowed intent key: " + key);
            }
        });

        String formType = normalizeFormType(textOrNull(node.path("formType")));
        if (!isAllowedFormType(formType)) {
            throw new IllegalArgumentException("unsupported formType");
        }
        String department = textOrNull(node.path("department"));
        if (department != null && !isAllowedDepartment(department, allowedDepartments)) {
            throw new IllegalArgumentException("department is not in whitelist");
        }
        if (department == null) {
            department = ALL;
        }
        String timeRange = textOrNull(node.path("timeRange"));
        if (timeRange != null && JijianQueryTimeRangeEnum.of(timeRange) == null) {
            throw new IllegalArgumentException("invalid timeRange");
        }

        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType(formType);
        intent.setDepartment(department);
        intent.setTimeRange(timeRange);
        intent.setMetrics(parseMetrics(node.path("metrics")));
        intent.setNeedDetail(node.path("needDetail").asBoolean(false));
        intent.setAnalysisGoal(textOrNull(node.path("analysisGoal")));
        intent.setDrillDownTarget(textOrNull(node.path("drillDownTarget")));
        if (node.path("confidence").isNumber()) {
            intent.setConfidence(node.path("confidence").asDouble());
        }
        return intent;
    }

    private String normalizeFormType(String formType) {
        if ("ATTENDANCE".equals(formType)) {
            return "ATTENDANCE";
        }
        if ("REAL_ESTATE".equals(formType)) {
            return "REAL_ESTATE";
        }
        if ("CANTEEN_SUPPLY".equals(formType)) {
            return "CANTEEN_SUPPLY";
        }
        return formType;
    }

    private boolean isAllowedFormType(String formType) {
        JijianQueryFormTypeEnum e = JijianQueryFormTypeEnum.of(formType);
        return e != null && e.isSupported();
    }

    private List<String> parseMetrics(JsonNode metricsNode) {
        List<String> metrics = new ArrayList<>();
        if (metricsNode.isArray()) {
            for (JsonNode metricNode : metricsNode) {
                String metric = textOrNull(metricNode);
                if (ALLOWED_METRICS.contains(metric) && !metrics.contains(metric)) {
                    metrics.add(metric);
                }
            }
        }
        if (metrics.isEmpty()) {
            metrics.add(JijianQueryMetricEnum.TOTAL.getValue());
        }
        return metrics;
    }

    private String callDeepSeek(EffectiveDeepSeekConfig cfg, String systemPrompt, String userMessage) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", cfg.model);
        body.put("temperature", 0.1);
        body.put("stream", false);
        ArrayNode messages = body.putArray("messages");
        messages.addObject().put("role", "system").put("content", systemPrompt);
        messages.addObject().put("role", "user").put("content", userMessage);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(safeTimeoutSeconds(cfg.timeoutSeconds)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.chatCompletionsUrl()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + cfg.apiKey)
                .timeout(Duration.ofSeconds(safeTimeoutSeconds(cfg.timeoutSeconds)))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek HTTP " + response.statusCode());
        }
        JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.isNull() || content.asText().isBlank()) {
            throw new IllegalStateException("DeepSeek response content is empty");
        }
        return content.asText().trim();
    }

    private String toSafeAggregateJson(String formType, String summaryJson) throws Exception {
        JsonNode root = objectMapper.readTree(summaryJson == null || summaryJson.isBlank() ? "{}" : summaryJson);
        ObjectNode safe = objectMapper.createObjectNode();
        safe.put("formType", formType);
        safe.set("fieldWhitelist", readTreeQuiet(fieldWhitelist.describe(formType)));
        if (root.isObject()) {
            root.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (!"phone".equalsIgnoreCase(key) && !"idCard".equalsIgnoreCase(key) && !"businessLicense".equalsIgnoreCase(key)) {
                    safe.set(key, entry.getValue());
                }
            });
        }
        return objectMapper.writeValueAsString(safe);
    }

    private EffectiveDeepSeekConfig effectiveConfig() {
        EffectiveDeepSeekConfig cfg = new EffectiveDeepSeekConfig();
        JijianProperties.Ai.Deepseek deepseek = jijianProperties.getAi().getDeepseek();
        JijianProperties.Query.Ai legacy = jijianProperties.getQuery().getAi();
        cfg.enabled = deepseek.isEnabled();
        cfg.baseUrl = deepseek.getBaseUrl();
        cfg.model = deepseek.getModel();
        cfg.apiKey = deepseek.getApiKey();
        cfg.timeoutSeconds = deepseek.getTimeoutSeconds();
        cfg.summaryEnabled = legacy.isSummaryEnabled();
        cfg.maxHistoryRounds = legacy.getMaxHistoryRounds();
        if (!cfg.enabled && legacy.isEnabled()) {
            cfg.enabled = true;
            cfg.baseUrl = legacy.getApiUrl();
            cfg.model = legacy.getIntentModel();
            cfg.apiKey = legacy.getApiKey();
            cfg.timeoutSeconds = legacy.getTimeoutSeconds();
        }
        return cfg;
    }

    private boolean isAllowedDepartment(String department, List<String> allowedDepartments) {
        return ALL.equals(department) || (allowedDepartments != null && allowedDepartments.contains(department));
    }

    private boolean hasUsableApiKey(String key) {
        return key != null && !key.isBlank() && !key.startsWith("${") && !"CHANGE_ME".equals(key);
    }

    private int safeTimeoutSeconds(int timeoutSeconds) {
        return Math.max(1, timeoutSeconds);
    }

    private JsonNode readTreeQuiet(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String stripCodeFence(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("(?s)^```json\\s*", "")
                .replaceAll("(?s)^```\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();
    }

    private String limit(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static class EffectiveDeepSeekConfig {
        private boolean enabled;
        private boolean summaryEnabled = true;
        private String baseUrl;
        private String model;
        private String apiKey;
        private int timeoutSeconds;
        private int maxHistoryRounds = 6;

        private String chatCompletionsUrl() {
            String url = baseUrl == null || baseUrl.isBlank() ? "https://api.deepseek.com" : baseUrl.trim();
            if (url.endsWith("/chat/completions") || url.endsWith("/v1/chat/completions")) {
                return url;
            }
            return url.replaceAll("/+$", "") + "/chat/completions";
        }
    }
}
