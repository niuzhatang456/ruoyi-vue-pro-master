package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryMetricEnum;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.ai.IntentResult;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianAiIntentClient;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class JijianQueryChatServiceImpl implements JijianQueryChatService {

    private static final String FORM_TYPE_ATTENDANCE = "ATTENDANCE";
    private static final String DEPARTMENT_ALL = "ALL";
    private static final List<String> DEFAULT_METRICS = Arrays.asList(
            JijianQueryMetricEnum.TOTAL.getValue(),
            JijianQueryMetricEnum.BY_DEPARTMENT.getValue(),
            JijianQueryMetricEnum.BY_ATTENDANCE_STATUS.getValue()
    );

    @Resource
    private JijianAiIntentClient aiIntentClient;
    @Resource
    private JijianAttendanceQueryService attendanceQueryService;
    @Resource
    private JijianFormQueryHandlerRegistry handlerRegistry;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public JijianQueryChatRespVO chat(JijianQueryChatReqVO req) {
        JijianQueryChatRespVO resp = new JijianQueryChatRespVO();
        resp.setConversationId(req.getConversationId() != null ? req.getConversationId() : UUID.randomUUID().toString());

        IntentResult intentResult = parseIntentSafely(req);
        boolean intentUsedAi = intentResult.isAiGenerated();
        JijianAiQueryIntent intent = sanitizeIntent(intentResult.getIntent(), req);
        resp.setQueryIntent(intent);

        Object summaryObj = queryAggregateSafely(intent);
        resp.setData(summaryObj);

        SummaryResult summaryResult = generateSummarySafely(intent, summaryObj);
        resp.setAnswer(summaryResult.getText());
        resp.setAiMode(resolveAiMode(intentUsedAi, summaryResult.isAiGenerated()));
        return resp;
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
            JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(FORM_TYPE_ATTENDANCE);
            if (handler == null || !handler.isSupported()) {
                log.warn("[QueryChat] ATTENDANCE handler is unavailable");
                return null;
            }
            return handler.summaryByIntent(intent);
        } catch (Exception e) {
            log.error("[QueryChat] Attendance aggregate query failed", e);
            return null;
        }
    }

    private SummaryResult generateSummarySafely(JijianAiQueryIntent intent, Object summaryObj) {
        try {
            return aiIntentClient.generateSummary(intent, toJsonQuiet(summaryObj));
        } catch (Exception e) {
            log.warn("[QueryChat] Summary generation failed, using local text. reason={}", e.getMessage());
            return new SummaryResult("查询完成，请查看统计结果。", false);
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
            return buildDefaultIntent(req);
        }

        intent.setFormType(FORM_TYPE_ATTENDANCE);
        if (!isAllowedDepartment(intent.getDepartment())) {
            intent.setDepartment(defaultDepartment(req));
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
        return intent;
    }

    private JijianAiQueryIntent buildDefaultIntent(JijianQueryChatReqVO req) {
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType(FORM_TYPE_ATTENDANCE);
        intent.setDepartment(defaultDepartment(req));
        intent.setTimeRange(defaultTimeRange(req));
        intent.setMetrics(DEFAULT_METRICS);
        intent.setNeedDetail(false);
        return intent;
    }

    private String defaultDepartment(JijianQueryChatReqVO req) {
        return isAllowedDepartment(req.getDepartment()) ? req.getDepartment() : DEPARTMENT_ALL;
    }

    private String defaultTimeRange(JijianQueryChatReqVO req) {
        return JijianQueryTimeRangeEnum.of(req.getTimeRange()) == null ? "ONE_WEEK" : req.getTimeRange();
    }

    private boolean isAllowedDepartment(String department) {
        if (DEPARTMENT_ALL.equals(department)) {
            return true;
        }
        return department != null && attendanceQueryService.listDepartments().contains(department);
    }

    private String toJsonQuiet(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
