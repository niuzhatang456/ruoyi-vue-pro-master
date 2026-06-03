package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the P3 local fallback client.
 */
class LocalFallbackFormTypeTest {

    private LocalFallbackAiIntentClient client;

    @BeforeEach
    void setUp() {
        client = new LocalFallbackAiIntentClient();
    }

    private JijianQueryChatReqVO req(String formType, String message) {
        JijianQueryChatReqVO r = new JijianQueryChatReqVO();
        r.setFormType(formType);
        r.setMessage(message);
        r.setDepartment("ALL");
        r.setTimeRange("ONE_WEEK");
        return r;
    }

    @Test
    void attendanceKeyword_remainsAttendance() {
        IntentResult result = client.parseIntent(req("ATTENDANCE", "attendance summary"));
        assertEquals("ATTENDANCE", result.getIntent().getFormType());
        assertFalse(result.isAiGenerated());
    }

    @Test
    void unsupportedFrontendFormType_fallsBackToAttendance() {
        IntentResult result = client.parseIntent(req("REAL_ESTATE", "summary"));
        assertEquals("ATTENDANCE", result.getIntent().getFormType());
        assertFalse(result.isAiGenerated());
    }

    @Test
    void propertyKeywords_doNotEnableP4() {
        IntentResult result = client.parseIntent(req("ATTENDANCE", "real estate property lease area"));
        assertEquals("ATTENDANCE", result.getIntent().getFormType());
        assertFalse(result.isAiGenerated());
    }

    @Test
    void defaultFormType_isAttendance() {
        IntentResult result = client.parseIntent(req(null, "today summary"));
        assertEquals("ATTENDANCE", result.getIntent().getFormType());
    }

    @Test
    void localFallback_aiGeneratedAlwaysFalse() {
        IntentResult r1 = client.parseIntent(req("ATTENDANCE", "attendance"));
        IntentResult r2 = client.parseIntent(req("REAL_ESTATE", "property"));
        assertFalse(r1.isAiGenerated());
        assertFalse(r2.isAiGenerated());
    }

    @Test
    void generateSummary_attendanceUsesLocalTemplate() {
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("ATTENDANCE");
        intent.setTimeRange("ONE_WEEK");
        intent.setDepartment("ALL");
        SummaryResult summary = client.generateSummary(intent, "{}");
        assertFalse(summary.isAiGenerated());
        assertNotBlank(summary.getText());
    }

    private void assertNotBlank(String text) {
        assertNotNull(text);
        assertFalse(text.trim().isEmpty());
    }
}
