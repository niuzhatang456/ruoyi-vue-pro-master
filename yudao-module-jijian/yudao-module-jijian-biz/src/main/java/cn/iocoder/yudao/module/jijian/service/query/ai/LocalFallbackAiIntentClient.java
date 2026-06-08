package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class LocalFallbackAiIntentClient implements JijianAiIntentClient {

    @Override
    public IntentResult parseIntent(JijianQueryChatReqVO req) {
        String msg = req.getMessage() == null ? "" : req.getMessage();
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType(parseFormType(msg, req.getFormType()));
        intent.setDepartment(parseDepartment(msg, req.getDepartment()));
        intent.setTimeRange(parseTimeRange(msg, req.getTimeRange()));
        intent.setMetrics(Arrays.asList("TOTAL", "BY_DEPARTMENT", "BY_ATTENDANCE_STATUS"));
        intent.setNeedDetail(msg.contains("\u660e\u7ec6") || msg.contains("\u8be6\u60c5") || msg.contains("\u5217\u8868"));
        intent.setAnalysisGoal(msg);
        return new IntentResult(intent, false);
    }

    @Override
    public SummaryResult generateSummary(JijianAiQueryIntent intent, String summaryJson) {
        if (JijianQueryFormTypeEnum.CANTEEN_SUPPLIER.getValue().equals(intent.getFormType()) && containsCanteenForbiddenGoal(intent.getAnalysisGoal())) {
            return new SummaryResult("\u5f53\u524d\u98df\u5802\u4f9b\u5e94\u5546\u4fe1\u606f\u8868\u4ec5\u5305\u542b\u9879\u76ee\u540d\u79f0\u3001\u89c4\u683c/\u7b49\u7ea7\u3001\u5355\u4f4d\u3001\u4ef7\u683c\u3001\u91c7\u4ef7\u70b9\uff0c\u4e0d\u5305\u542b\u90e8\u95e8\u3001\u6d6a\u8d39\u3001\u8fdd\u89c4\u3001\u5f02\u5e38\u5b57\u6bb5\uff0c\u56e0\u6b64\u4e0d\u80fd\u5206\u6790\u90e8\u95e8\u6d6a\u8d39\u60c5\u51b5\uff1b\u53ef\u4ee5\u5206\u6790\u540c\u4e00\u9879\u76ee\u5728\u4e0d\u540c\u91c7\u4ef7\u70b9\u7684\u4ef7\u683c\u5dee\u5f02\u3002", false);
        }
        return new SummaryResult("\u3010\u672c\u5730\u89c4\u5219\u5206\u6790\u3011\u5df2\u6309 " + intent.getFormType()
                + " / " + intent.getTimeRange() + " \u8fd4\u56de\u5355\u8868\u7edf\u8ba1\u7ed3\u679c\uff0c\u8bf7\u67e5\u770b\u4e0b\u65b9 summary \u548c\u5217\u8868\u9884\u89c8\u3002", false);
    }

    private String parseFormType(String msg, String defaultFormType) {
        if (defaultFormType != null && !defaultFormType.isBlank()) {
            return normalize(defaultFormType);
        }
        if (msg.contains("\u98df\u5802") || msg.contains("\u4f9b\u5e94") || msg.contains("\u91c7\u4ef7")) {
            return JijianQueryFormTypeEnum.CANTEEN_SUPPLIER.getValue();
        }
        if (msg.contains("\u79df\u8d41\u5408\u540c") || msg.contains("\u5408\u540c\u91d1\u989d") || msg.contains("\u5230\u671f\u5408\u540c")) {
            return JijianQueryFormTypeEnum.LEASE_CONTRACT.getValue();
        }
        if (msg.contains("\u79df\u8d41\u4eba\u5458") || msg.contains("\u79df\u5ba2") || msg.contains("\u8054\u7cfb\u4eba")) {
            return JijianQueryFormTypeEnum.LESSEE.getValue();
        }
        if (msg.contains("\u623f\u4ea7") || msg.contains("\u4e0d\u52a8\u4ea7") || msg.contains("\u4ea7\u6743")) {
            return JijianQueryFormTypeEnum.PROPERTY_INFO.getValue();
        }
        if (msg.contains("\u7597\u4f11\u517b")) {
            return JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue();
        }
        if (msg.contains("\u4e8b\u5047")) {
            return JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue();
        }
        if (msg.contains("\u51fa\u5dee")) {
            return JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue();
        }
        if (msg.contains("\u8c03\u4f11")) {
            return JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue();
        }
        return JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue();
    }

    private String normalize(String formType) {
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
        return e == null ? JijianQueryFormTypeEnum.ATTENDANCE_DAILY.getValue() : e.getValue();
    }

    private String parseDepartment(String msg, String defaultDept) {
        if (msg.contains("\u5168\u90e8") || msg.contains("\u5168\u5355\u4f4d") || msg.contains("\u6240\u6709\u90e8\u95e8")) {
            return "ALL";
        }
        return defaultDept != null ? defaultDept : "ALL";
    }

    private String parseTimeRange(String msg, String defaultRange) {
        if (msg.contains("\u4e00\u5e74") || msg.contains("12\u4e2a\u6708")) {
            return "ONE_YEAR";
        }
        if (msg.contains("\u534a\u5e74") || msg.contains("6\u4e2a\u6708")) {
            return "HALF_YEAR";
        }
        if (msg.contains("\u4e09\u4e2a\u6708") || msg.contains("3\u4e2a\u6708") || msg.contains("\u5b63\u5ea6")) {
            return "THREE_MONTHS";
        }
        if (msg.contains("\u4e00\u4e2a\u6708") || msg.contains("1\u4e2a\u6708") || msg.contains("\u672c\u6708") || msg.contains("\u4e0a\u4e2a\u6708")) {
            return "ONE_MONTH";
        }
        if (msg.contains("\u4e00\u5468") || msg.contains("\u4e03\u5929") || msg.contains("7\u5929")) {
            return "ONE_WEEK";
        }
        if (msg.contains("\u4eca\u65e5") || msg.contains("\u4eca\u5929") || msg.contains("\u4e00\u5929")) {
            return "ONE_DAY";
        }
        return defaultRange != null ? defaultRange : "ONE_WEEK";
    }

    private boolean containsCanteenForbiddenGoal(String text) {
        if (text == null) {
            return false;
        }
        return text.contains("\u90e8\u95e8") || text.contains("\u6d6a\u8d39") || text.contains("\u8fdd\u89c4")
                || text.contains("\u5f02\u5e38") || text.contains("\u4eba\u5458") || text.contains("\u5ba1\u6279")
                || text.contains("\u5907\u6ce8") || text.contains("\u4f9b\u5e94\u6b21\u6570");
    }
}
