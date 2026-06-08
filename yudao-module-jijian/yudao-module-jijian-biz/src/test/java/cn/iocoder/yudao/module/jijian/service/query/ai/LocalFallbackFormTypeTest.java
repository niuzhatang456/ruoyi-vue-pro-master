package cn.iocoder.yudao.module.jijian.service.query.ai;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    void defaultFormType_isAttendanceDaily() {
        IntentResult result = client.parseIntent(req(null, "today summary"));
        assertEquals("ATTENDANCE_DAILY", result.getIntent().getFormType());
        assertFalse(result.isAiGenerated());
    }

    @Test
    void compatibilityAttendance_normalizesToAttendanceDaily() {
        IntentResult result = client.parseIntent(req("ATTENDANCE", "attendance summary"));
        assertEquals("ATTENDANCE_DAILY", result.getIntent().getFormType());
    }

    @Test
    void propertyKeyword_detectsPropertyInfo() {
        IntentResult result = client.parseIntent(req(null, "统计房产产权和租赁情况"));
        assertEquals("PROPERTY_INFO", result.getIntent().getFormType());
    }

    @Test
    void contractKeyword_detectsLeaseContract() {
        IntentResult result = client.parseIntent(req(null, "分析租赁合同金额和即将到期合同"));
        assertEquals("LEASE_CONTRACT", result.getIntent().getFormType());
    }

    @Test
    void lesseeKeyword_detectsLessee() {
        IntentResult result = client.parseIntent(req(null, "统计租赁人员和联系人完整性"));
        assertEquals("LESSEE", result.getIntent().getFormType());
    }

    @Test
    void canteenKeyword_detectsCanteenSupplier() {
        IntentResult result = client.parseIntent(req(null, "分析食堂供应商不同采价点价格差异"));
        assertEquals("CANTEEN_SUPPLIER", result.getIntent().getFormType());
    }

    @Test
    void compatibilityRealEstate_normalizesToPropertyInfo() {
        IntentResult result = client.parseIntent(req("REAL_ESTATE", "summary"));
        assertEquals("PROPERTY_INFO", result.getIntent().getFormType());
    }

    @Test
    void canteenForbiddenGoal_returnsFieldBoundaryText() {
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("CANTEEN_SUPPLIER");
        intent.setTimeRange("ONE_MONTH");
        intent.setDepartment("ALL");
        intent.setAnalysisGoal("哪个部门食堂浪费最严重");
        SummaryResult summary = client.generateSummary(intent, "{}");
        assertFalse(summary.isAiGenerated());
        assertTrue(summary.getText().contains("不包含部门"));
        assertTrue(summary.getText().contains("不能分析"));
    }

    @Test
    void deepSeekPrompts_containFieldWhitelistAndNoSqlRules() {
        JijianDeepSeekIntentClient deepSeek = new JijianDeepSeekIntentClient();
        String intentPrompt = deepSeek.buildIntentSystemPrompt();
        String summaryPrompt = deepSeek.buildSummarySystemPrompt();

        assertTrue(intentPrompt.contains("字段白名单"));
        assertTrue(intentPrompt.contains("不得生成 SQL"));
        assertTrue(summaryPrompt.contains("字段白名单"));
        assertTrue(summaryPrompt.contains("不得生成 SQL"));
        assertTrue(summaryPrompt.contains("不得输出手机号"));
    }

    @Test
    void ninePrimaryFormTypes_areSupported() {
        int count = 0;
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                assertTrue(e.isSupported());
                count++;
            }
        }
        assertEquals(9, count);
    }
}
