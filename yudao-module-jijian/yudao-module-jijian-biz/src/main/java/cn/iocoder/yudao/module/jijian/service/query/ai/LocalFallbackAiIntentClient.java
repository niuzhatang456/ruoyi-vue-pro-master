package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Local rule-based fallback. It is always available and never calls an
 * external model.
 */
@Component
public class LocalFallbackAiIntentClient implements JijianAiIntentClient {

    private static final List<String> DEFAULT_METRICS = Arrays.asList(
            "TOTAL",
            "BY_DEPARTMENT",
            "BY_ATTENDANCE_STATUS"
    );

    @Override
    public IntentResult parseIntent(JijianQueryChatReqVO req) {
        String msg = req.getMessage() == null ? "" : req.getMessage();

        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("ATTENDANCE");
        intent.setDepartment(parseDepartment(msg, req.getDepartment()));
        intent.setTimeRange(parseTimeRange(msg, req.getTimeRange()));
        intent.setMetrics(DEFAULT_METRICS);
        intent.setNeedDetail(msg.contains("明细") || msg.contains("详情") || msg.contains("列表"));
        return new IntentResult(intent, false);
    }

    @Override
    public SummaryResult generateSummary(JijianAiQueryIntent intent, String summaryJson) {
        String text = String.format("【本地规则模式】已按 %s 范围统计 %s 的考勤聚合数据，请查看下方统计结果。",
                intent.getTimeRange(),
                "ALL".equals(intent.getDepartment()) ? "全单位" : intent.getDepartment());
        return new SummaryResult(text, false);
    }

    private String parseDepartment(String msg, String defaultDept) {
        if (msg.contains("全单位") || msg.contains("全部") || msg.contains("所有部门")) {
            return "ALL";
        }
        return defaultDept != null ? defaultDept : "ALL";
    }

    private String parseTimeRange(String msg, String defaultRange) {
        if (msg.contains("一年") || msg.contains("1年") || msg.contains("12个月")) {
            return "ONE_YEAR";
        }
        if (msg.contains("半年") || msg.contains("6个月")) {
            return "HALF_YEAR";
        }
        if (msg.contains("三个月") || msg.contains("3个月") || msg.contains("季度")) {
            return "THREE_MONTHS";
        }
        if (msg.contains("一个月") || msg.contains("1个月") || msg.contains("本月")) {
            return "ONE_MONTH";
        }
        if (msg.contains("一周") || msg.contains("1周") || msg.contains("七天") || msg.contains("7天")) {
            return "ONE_WEEK";
        }
        if (msg.contains("今日") || msg.contains("今天") || msg.contains("一日") || msg.contains("1天")) {
            return "ONE_DAY";
        }
        return defaultRange != null ? defaultRange : "ONE_WEEK";
    }
}
