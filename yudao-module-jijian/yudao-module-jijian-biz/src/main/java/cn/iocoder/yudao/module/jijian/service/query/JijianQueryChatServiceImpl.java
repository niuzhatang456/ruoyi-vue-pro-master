package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryMetricEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceAnalysisService.AnalysisResult;
import cn.iocoder.yudao.module.jijian.service.query.ai.IntentResult;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianAiIntentClient;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianDeepSeekIntentClient;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianQueryFieldWhitelist;
import cn.iocoder.yudao.module.jijian.service.query.ai.SummaryResult;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import cn.iocoder.yudao.module.jijian.service.query.handler.JijianFormQueryHandler;
import cn.iocoder.yudao.module.jijian.service.query.handler.JijianFormQueryHandlerRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class JijianQueryChatServiceImpl implements JijianQueryChatService {

    private static final String ALL = "ALL";
    private static final List<String> DEFAULT_METRICS = Arrays.asList(
            JijianQueryMetricEnum.TOTAL.getValue(),
            JijianQueryMetricEnum.BY_DEPARTMENT.getValue(),
            JijianQueryMetricEnum.BY_ATTENDANCE_STATUS.getValue()
    );

    @Resource
    private JijianAiIntentClient aiIntentClient;
    @Resource
    private JijianDeepSeekIntentClient deepSeekIntentClient;
    @Resource
    private JijianAttendanceAnalysisService attendanceAnalysisService;
    @Resource
    private JijianFormQueryHandlerRegistry handlerRegistry;
    @Resource
    private ObjectMapper objectMapper;
    @Resource
    private JijianQueryFieldWhitelist fieldWhitelist;

    @Override
    public JijianQueryChatRespVO chat(JijianQueryChatReqVO req) {
        JijianQueryChatRespVO resp = new JijianQueryChatRespVO();
        resp.setConversationId(req.getConversationId() != null ? req.getConversationId() : UUID.randomUUID().toString());

        IntentResult intentResult = parseIntentSafely(req);
        JijianAiQueryIntent intent = sanitizeIntent(intentResult.getIntent(), req);
        resp.setQueryIntent(intent);
        resp.setFormType(intent.getFormType());

        // 考勤5表综合分析路径
        if (isAttendanceFormType(intent.getFormType())) {
            return fillAttendanceAnalysis(resp, intent, req, intentResult.isAiGenerated());
        }

        Object summaryObj = queryAggregateSafely(intent);
        resp.setData(summaryObj);
        resp.setSummary(summaryObj == null ? Collections.emptyMap() : summaryObj);

        JijianQueryPageRespVO preview = queryPreviewPageSafely(intent);
        resp.setPageResult(safePageResult(preview));
        resp.setColumns(preview == null || preview.getColumns() == null ? Collections.emptyList() : preview.getColumns());

        SummaryResult summaryResult = generateSummarySafely(intent, summaryObj, req.getMessage());
        resp.setAnswer(summaryResult.getText());
        resp.setAiMode(resolveAiMode(intentResult.isAiGenerated(), summaryResult.isAiGenerated()));
        return resp;
    }

    private boolean isAttendanceFormType(String formType) {
        return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue().equals(formType)
                || "ATTENDANCE".equals(formType);
    }

    private JijianQueryChatRespVO fillAttendanceAnalysis(JijianQueryChatRespVO resp,
                                                          JijianAiQueryIntent intent,
                                                          JijianQueryChatReqVO req,
                                                          boolean intentUsedAi) {
        try {
            AnalysisResult analysisResult = attendanceAnalysisService.analyze(
                    intent.getDepartment(), intent.getTimeRange());

            resp.setMetrics(analysisResult.getMetrics());
            resp.setCharts(analysisResult.getCharts());
            resp.setTables(analysisResult.getTables());
            resp.setDatabaseContextMeta(analysisResult.getDatabaseContextMeta());

            // summary 兼容旧字段
            Map<String, Object> summaryCompat = buildAttendanceSummaryCompat(analysisResult);
            resp.setData(summaryCompat);
            resp.setSummary(summaryCompat);
            resp.setPageResult(new PageResult<>(Collections.emptyList(), 0L));
            resp.setColumns(Collections.emptyList());

            // DeepSeek 基于真实数据包分析
            String answer = deepSeekIntentClient.analyzeAttendanceData(analysisResult, req.getMessage());
            resp.setAnswer(answer);
            resp.setAiMode(JijianAiIntentClient.MODE_DEEPSEEK_DATA_ANALYSIS);
        } catch (Exception e) {
            log.error("[QueryChat] Attendance analysis failed", e);
            resp.setAnswer("考勤数据分析失败，请稍后重试。");
            resp.setAiMode(JijianAiIntentClient.MODE_LOCAL_FALLBACK);
        }
        return resp;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildAttendanceSummaryCompat(AnalysisResult r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("formType", "ATTENDANCE_DAILY");
        m.put("totalCount", r.getAttendanceList().size());
        m.put("rawAnomalyCount", r.getRawAnomalies().size());
        m.put("explainedCount", r.getExplainedAnomalies().size());
        m.put("suspectedAbsenceCount", r.getUnexplainedAnomalies().size());
        return m;
    }

    private IntentResult parseIntentSafely(JijianQueryChatReqVO req) {
        try {
            return aiIntentClient.parseIntent(req);
        } catch (Exception e) {
            log.warn("[QueryChat] Intent parsing failed, using local default. reason={}", e.getMessage());
            return new IntentResult(buildDefaultIntent(req), false);
        }
    }

    private Object queryAggregateSafely(JijianAiQueryIntent intent) {
        try {
            JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(intent.getFormType());
            if (handler == null || !handler.isSupported()) {
                log.warn("[QueryChat] handler is unavailable, formType={}", intent.getFormType());
                return Collections.emptyMap();
            }
            Object summary = handler.summaryByIntent(intent);
            return summary == null ? Collections.emptyMap() : summary;
        } catch (Exception e) {
            log.error("[QueryChat] aggregate query failed, formType={}", intent.getFormType(), e);
            return Collections.emptyMap();
        }
    }

    private JijianQueryPageRespVO queryPreviewPageSafely(JijianAiQueryIntent intent) {
        try {
            JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(intent.getFormType());
            if (handler == null || !handler.isSupported()) {
                return null;
            }
            JijianQueryPageReqVO req = new JijianQueryPageReqVO();
            req.setFormType(intent.getFormType());
            req.setDepartment(intent.getDepartment());
            req.setTimeRange(intent.getTimeRange());
            req.setPageNo(1);
            req.setPageSize(10);
            return handler.genericPageQuery(req);
        } catch (Exception e) {
            log.warn("[QueryChat] preview page query failed, formType={}, reason={}", intent.getFormType(), e.getMessage());
            return null;
        }
    }

    private PageResult<Map<String, Object>> safePageResult(JijianQueryPageRespVO preview) {
        if (preview == null || preview.getPageResult() == null) {
            return new PageResult<>(Collections.emptyList(), 0L);
        }
        List<Map<String, Object>> list = preview.getPageResult().getList();
        Long total = preview.getPageResult().getTotal();
        return new PageResult<>(list == null ? Collections.emptyList() : list, total == null ? 0L : total);
    }

    private SummaryResult generateSummarySafely(JijianAiQueryIntent intent, Object summaryObj, String rawMessage) {
        try {
            if (isForbiddenByFieldWhitelist(intent, rawMessage)) {
                return new SummaryResult(buildFieldBoundaryAnswer(intent), false);
            }
            if (isCrossTableQuestion(intent, rawMessage)) {
                return new SummaryResult("\u5f53\u524d\u67e5\u8be2\u6309\u5355\u8868\u6267\u884c\u3002\u623f\u4ea7\u9762\u79ef\u5c5e\u4e8e\u623f\u4ea7\u60c5\u51b5\u8868\uff0c\u5408\u540c\u91d1\u989d\u5c5e\u4e8e\u79df\u8d41\u5408\u540c\u8868\uff0c\u79df\u8d41\u4eba\u5458\u4fe1\u606f\u5c5e\u4e8e\u79df\u8d41\u4eba\u5458\u8868\uff1b\u8bf7\u9009\u62e9\u5176\u4e2d\u4e00\u5f20\u8868\u5206\u522b\u5206\u6790\uff0c\u8de8\u8868\u5173\u8054\u5206\u6790\u540e\u7eed\u5355\u72ec\u5b9e\u73b0\u3002", false);
            }
            return aiIntentClient.generateSummary(intent, toJsonQuiet(summaryObj));
        } catch (Exception e) {
            log.warn("[QueryChat] Summary generation failed, using local text. reason={}", e.getMessage());
            return new SummaryResult("\u67e5\u8be2\u5b8c\u6210\uff0c\u8bf7\u67e5\u770b\u7edf\u8ba1\u7ed3\u679c\u3002", false);
        }
    }

    private String resolveAiMode(boolean intentUsedAi, boolean summaryUsedAi) {
        if (!intentUsedAi) {
            return JijianAiIntentClient.MODE_LOCAL_FALLBACK;
        }
        return summaryUsedAi ? JijianAiIntentClient.MODE_DEEPSEEK_SUMMARY : JijianAiIntentClient.MODE_DEEPSEEK_INTENT;
    }

    private JijianAiQueryIntent sanitizeIntent(JijianAiQueryIntent intent, JijianQueryChatReqVO req) {
        if (intent == null) {
            intent = buildDefaultIntent(req);
        }

        intent.setFormType(resolveFormType(intent.getFormType(), req));
        if (!isAllowedDepartment(intent.getDepartment(), intent.getFormType())) {
            intent.setDepartment(defaultDepartment(req, intent.getFormType()));
        }
        if (JijianQueryTimeRangeEnum.of(intent.getTimeRange()) == null) {
            intent.setTimeRange(defaultTimeRange(req));
        }

        List<String> metrics = new ArrayList<>();
        if (intent.getMetrics() != null) {
            for (String metric : intent.getMetrics()) {
                if (JijianQueryMetricEnum.of(metric) != null
                        && !JijianQueryMetricEnum.DETAIL_SAMPLE.getValue().equals(metric)
                        && !metrics.contains(metric)) {
                    metrics.add(metric);
                }
            }
        }
        if (metrics.isEmpty()) {
            metrics.addAll(DEFAULT_METRICS);
        }
        intent.setMetrics(metrics);
        if (intent.getAnalysisGoal() == null || intent.getAnalysisGoal().isBlank()) {
            intent.setAnalysisGoal(req.getMessage());
        }
        intent.setOriginalMessage(req.getMessage());
        return intent;
    }

    private JijianAiQueryIntent buildDefaultIntent(JijianQueryChatReqVO req) {
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType(resolveFormType(null, req));
        intent.setDepartment(defaultDepartment(req, intent.getFormType()));
        intent.setTimeRange(defaultTimeRange(req));
        intent.setMetrics(DEFAULT_METRICS);
        intent.setNeedDetail(false);
        intent.setAnalysisGoal(req.getMessage());
        return intent;
    }

    private String resolveFormType(String aiFormType, JijianQueryChatReqVO req) {
        if (req.getFormType() != null && !req.getFormType().isBlank()) {
            return normalizeSupportedFormType(req.getFormType());
        }
        String candidate = aiFormType;
        if (candidate == null || candidate.isBlank()) {
            candidate = inferFormType(req.getMessage());
        }
        if ((candidate == null || candidate.isBlank()) && req.getHistory() != null) {
            int start = Math.max(0, req.getHistory().size() - 12);
            for (int i = req.getHistory().size() - 1; i >= start; i--) {
                JijianQueryChatReqVO.ChatHistoryItem item = req.getHistory().get(i);
                if (item != null) {
                    candidate = inferFormType(item.getContent());
                    if (candidate != null) {
                        break;
                    }
                }
            }
        }
        return normalizeSupportedFormType(candidate);
    }

    private String normalizeSupportedFormType(String formType) {
        if (formType == null || formType.isBlank()) {
            return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue();
        }
        if ("ATTENDANCE".equals(formType)) {
            return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue();
        }
        if ("REAL_ESTATE".equals(formType)) {
            return JijianQueryFormTypeEnum.PROPERTY_INFO.getValue();
        }
        if ("CANTEEN_SUPPLY".equals(formType)) {
            return JijianQueryFormTypeEnum.CANTEEN_SUPPLIER.getValue();
        }
        if ("LEAVE_HEALTH".equals(formType)) {
            return JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue();
        }
        if ("LEAVE_PERSONAL".equals(formType)) {
            return JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue();
        }
        if ("COMPENSATORY".equals(formType)) {
            return JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue();
        }
        JijianQueryFormTypeEnum e = JijianQueryFormTypeEnum.of(formType);
        if (e != null && e.isSupported()) {
            return e.getValue();
        }
        return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue();
    }

    private String inferFormType(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("\u98df\u5802") || text.contains("\u4f9b\u5e94") || text.contains("\u91c7\u4ef7")
                || text.toLowerCase().contains("canteen")) {
            return JijianQueryFormTypeEnum.CANTEEN_SUPPLIER.getValue();
        }
        if (text.contains("\u79df\u8d41\u5408\u540c") || text.contains("\u5408\u540c\u91d1\u989d") || text.contains("\u5230\u671f\u5408\u540c")) {
            return JijianQueryFormTypeEnum.LEASE_CONTRACT.getValue();
        }
        if (text.contains("\u79df\u8d41\u4eba\u5458") || text.contains("\u79df\u5ba2") || text.contains("\u8054\u7cfb\u4eba")) {
            return JijianQueryFormTypeEnum.LESSEE.getValue();
        }
        if (text.contains("\u623f\u4ea7") || text.contains("\u4e0d\u52a8\u4ea7") || text.contains("\u4ea7\u6743")
                || text.toLowerCase().contains("property")) {
            return JijianQueryFormTypeEnum.PROPERTY_INFO.getValue();
        }
        if (text.contains("\u7597\u4f11\u517b")) {
            return JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue();
        }
        if (text.contains("\u4e8b\u5047")) {
            return JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue();
        }
        if (text.contains("\u51fa\u5dee")) {
            return JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue();
        }
        if (text.contains("\u8c03\u4f11")) {
            return JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue();
        }
        if (text.contains("\u8003\u52e4") || text.contains("\u51fa\u52e4")) {
            return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue();
        }
        return null;
    }

    private String defaultDepartment(JijianQueryChatReqVO req, String formType) {
        return isAllowedDepartment(req.getDepartment(), formType) ? req.getDepartment() : ALL;
    }

    private boolean isAllowedDepartment(String department, String formType) {
        if (ALL.equals(department)) {
            return true;
        }
        JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(normalizeSupportedFormType(formType));
        if (handler == null || department == null) {
            return false;
        }
        return handler.getDepartments().stream().anyMatch(item -> department.equals(item.getValue()));
    }

    private String defaultTimeRange(JijianQueryChatReqVO req) {
        if (JijianQueryTimeRangeEnum.of(req.getTimeRange()) != null) {
            return req.getTimeRange();
        }
        String inferred = inferTimeRange(req.getMessage());
        if (inferred != null) {
            return inferred;
        }
        if (req.getHistory() != null) {
            int start = Math.max(0, req.getHistory().size() - 12);
            for (int i = req.getHistory().size() - 1; i >= start; i--) {
                JijianQueryChatReqVO.ChatHistoryItem item = req.getHistory().get(i);
                if (item != null) {
                    inferred = inferTimeRange(item.getContent());
                    if (inferred != null) {
                        return inferred;
                    }
                }
            }
        }
        return JijianQueryTimeRangeEnum.ONE_YEAR.getValue();
    }

    private String inferTimeRange(String text) {
        if (text == null) {
            return null;
        }
        if (text.contains("\u4e00\u5e74") || text.contains("12\u4e2a\u6708")) {
            return "ONE_YEAR";
        }
        if (text.contains("\u534a\u5e74") || text.contains("6\u4e2a\u6708")) {
            return "HALF_YEAR";
        }
        if (text.contains("\u4e09\u4e2a\u6708") || text.contains("3\u4e2a\u6708") || text.contains("\u5b63\u5ea6")) {
            return "THREE_MONTHS";
        }
        if (text.contains("\u4e0a\u4e2a\u6708") || text.contains("\u672c\u6708") || text.contains("\u4e00\u4e2a\u6708") || text.contains("1\u4e2a\u6708")) {
            return "ONE_MONTH";
        }
        if (text.contains("\u4e00\u5468") || text.contains("\u4e03\u5929") || text.contains("7\u5929")) {
            return "ONE_WEEK";
        }
        if (text.contains("\u4eca\u5929") || text.contains("\u4eca\u65e5") || text.contains("\u4e00\u5929")) {
            return "ONE_DAY";
        }
        return null;
    }

    private boolean isForbiddenByFieldWhitelist(JijianAiQueryIntent intent, String rawMessage) {
        String text = (intent.getAnalysisGoal() == null ? "" : intent.getAnalysisGoal())
                + " " + (intent.getDrillDownTarget() == null ? "" : intent.getDrillDownTarget())
                + " " + (rawMessage == null ? "" : rawMessage);
        for (String forbidden : fieldWhitelist.forbiddenAnalyses(intent.getFormType())) {
            if (text.contains(forbidden)) {
                return true;
            }
        }
        return false;
    }

    private String buildFieldBoundaryAnswer(JijianAiQueryIntent intent) {
        String formType = intent.getFormType();
        if (JijianQueryFormTypeEnum.CANTEEN_SUPPLIER.getValue().equals(formType)) {
            return "\u5f53\u524d\u98df\u5802\u4f9b\u5e94\u5546\u4fe1\u606f\u8868\u4ec5\u5305\u542b\u9879\u76ee\u540d\u79f0\u3001\u89c4\u683c/\u7b49\u7ea7\u3001\u5355\u4f4d\u3001\u4ef7\u683c\u3001\u91c7\u4ef7\u70b9\uff0c\u4e0d\u5305\u542b\u90e8\u95e8\u3001\u6d6a\u8d39\u3001\u8fdd\u89c4\u3001\u5f02\u5e38\u3001\u4eba\u5458\u3001\u5ba1\u6279\u3001\u5907\u6ce8\u3001\u4f9b\u5e94\u6b21\u6570\u5b57\u6bb5\uff0c\u56e0\u6b64\u4e0d\u80fd\u5206\u6790\u8be5\u95ee\u9898\uff1b\u53ef\u4ee5\u5206\u6790\u540c\u4e00\u9879\u76ee\u5728\u4e0d\u540c\u91c7\u4ef7\u70b9\u7684\u4ef7\u683c\u5dee\u5f02\u3001\u6700\u9ad8\u4ef7\u3001\u6700\u4f4e\u4ef7\u3001\u5747\u4ef7\u3001\u5dee\u989d\u548c\u5dee\u5f02\u6bd4\u4f8b\u3002";
        }
        if (JijianQueryFormTypeEnum.PROPERTY_INFO.getValue().equals(formType)) {
            return "\u5f53\u524d\u623f\u4ea7\u60c5\u51b5\u8868\u4e0d\u5305\u542b\u8fdd\u89c4\u8ba4\u5b9a\u5b57\u6bb5\uff0c\u4e0d\u80fd\u76f4\u63a5\u5224\u65ad\u8fdd\u89c4\u62db\u79df\uff1b\u53ef\u4ee5\u57fa\u4e8e\u623f\u4ea7\u6570\u91cf\u3001\u9762\u79ef\u3001\u79df\u8d41\u60c5\u51b5\u3001\u4ea7\u6743\u4fe1\u606f\u548c\u5efa\u7b51\u65f6\u95f4\u505a\u5355\u8868\u7edf\u8ba1\u3002";
        }
        if (JijianQueryFormTypeEnum.LESSEE.getValue().equals(formType)) {
            return "\u5f53\u524d\u79df\u8d41\u4eba\u5458\u8868\u53ea\u652f\u6301\u4e2a\u4eba/\u7ec4\u7ec7\u3001\u8054\u7cfb\u4eba\u7ef4\u5ea6\u3001\u5355\u4f4d\u5185\u90e8\u4eba\u5458\u548c\u8bc1\u4ef6\u4fe1\u606f\u5b8c\u6574\u6027\u7edf\u8ba1\uff1b\u624b\u673a\u53f7\u3001\u8eab\u4efd\u8bc1\u3001\u8425\u4e1a\u6267\u7167\u4e0d\u4f1a\u539f\u6587\u8f93\u51fa\u6216\u53d1\u9001\u7ed9 AI\u3002";
        }
        return "\u5f53\u524d\u8868\u7684\u5b57\u6bb5\u767d\u540d\u5355\u4e0d\u652f\u6301\u5206\u6790\u8be5\u95ee\u9898\uff0c\u8bf7\u6539\u7528\u5f53\u524d\u8868\u5df2\u6709\u5b57\u6bb5\u8fdb\u884c\u5355\u8868\u7edf\u8ba1\u3002";
    }

    private boolean isCrossTableQuestion(JijianAiQueryIntent intent, String rawMessage) {
        JijianQueryFormTypeEnum e = JijianQueryFormTypeEnum.of(intent.getFormType());
        if (e == null || !e.isPrimary()) {
            return false;
        }
        String text = (intent.getAnalysisGoal() == null ? "" : intent.getAnalysisGoal())
                + " " + (intent.getDrillDownTarget() == null ? "" : intent.getDrillDownTarget())
                + " " + (rawMessage == null ? "" : rawMessage);
        int groups = 0;
        boolean g1 = text.contains("\u623f\u4ea7") || text.contains("\u9762\u79ef") || text.contains("\u4ea7\u6743") || text.contains("\u79df\u8d41\u60c5\u51b5");
        boolean g2 = text.contains("\u79df\u8d41\u4eba\u5458") || text.contains("\u79df\u5ba2") || text.contains("\u8054\u7cfb\u4eba") || text.contains("\u8eab\u4efd\u8bc1");
        boolean g3 = text.contains("\u5408\u540c") || text.contains("\u91d1\u989d") || text.contains("\u652f\u4ed8") || text.contains("\u5230\u671f");
        if (g1) groups++;
        if (g2) groups++;
        if (g3) groups++;
        log.debug("[CrossTable] formType={} rawLen={} g1={} g2={} g3={} groups={}", intent.getFormType(),
                rawMessage == null ? 0 : rawMessage.length(), g1, g2, g3, groups);
        return groups >= 2;
    }

    private String toJsonQuiet(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj == null ? Collections.emptyMap() : obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
