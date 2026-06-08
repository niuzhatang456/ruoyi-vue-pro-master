package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianDeepSeekIntentClient;
import cn.iocoder.yudao.module.jijian.service.query.ai.JijianQueryFieldWhitelist;
import cn.iocoder.yudao.module.jijian.service.query.ai.LocalFallbackAiIntentClient;
import cn.iocoder.yudao.module.jijian.service.query.ai.SummaryResult;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression guard: 防止 P4 查询模块关键约束被破坏。
 * 不依赖 Spring 上下文，纯单元测试。
 */
class RegressionGuardTest {

    // ── 1. /form-types 9 张 primary 表，兼容项不得出现 ──────────────────
    @Test
    void exactly9PrimaryFormTypes_legacyAliasesNotPrimary() {
        int primaryCount = 0;
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                primaryCount++;
                assertTrue(e.isSupported(), "Primary formType must be supported: " + e.getValue());
            }
        }
        assertEquals(9, primaryCount, "/form-types should return exactly 9 primary tables");

        // 兼容别名不得出现在 primary 列表
        assertFalse(JijianQueryFormTypeEnum.of("ATTENDANCE").isPrimary(), "ATTENDANCE alias must not be primary");
        assertFalse(JijianQueryFormTypeEnum.of("REAL_ESTATE").isPrimary(), "REAL_ESTATE alias must not be primary");
        assertFalse(JijianQueryFormTypeEnum.of("CANTEEN_SUPPLY").isPrimary(), "CANTEEN_SUPPLY alias must not be primary");
    }

    // ── 2. 跨表检测必须使用 rawMessage，不能只依赖 intent 精炼结果 ───────
    @Test
    void crossTableDetection_usesRawMessage_notOnlyRefinedIntent() throws Exception {
        JijianQueryChatServiceImpl service = new JijianQueryChatServiceImpl();

        // intent 精炼后只剩单表关键词（模拟 DeepSeek 删掉了跨表词）
        JijianAiQueryIntent strippedIntent = new JijianAiQueryIntent();
        strippedIntent.setFormType("PROPERTY_INFO");
        strippedIntent.setAnalysisGoal("分析房产面积");  // 只有 g1 group

        // rawMessage 保留了跨表词（用户原话）
        String rawMessage = "分析房产面积和合同金额的关系";  // g1 + g3 → groups=2 → cross-table

        Method isCross = JijianQueryChatServiceImpl.class
                .getDeclaredMethod("isCrossTableQuestion", JijianAiQueryIntent.class, String.class);
        isCross.setAccessible(true);

        boolean withRaw = (boolean) isCross.invoke(service, strippedIntent, rawMessage);
        boolean withoutRaw = (boolean) isCross.invoke(service, strippedIntent, null);

        assertTrue(withRaw, "Cross-table should be detected when rawMessage contains keywords from multiple table groups");
        assertFalse(withoutRaw, "Without rawMessage, stripped intent should not trigger cross-table detection");
    }

    // ── 3. CANTEEN_SUPPLIER 不能分析部门/浪费/违规/异常 ─────────────────
    @Test
    void canteenSupplier_forbiddenGoals_returnsBoundaryText() {
        LocalFallbackAiIntentClient client = new LocalFallbackAiIntentClient();
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("CANTEEN_SUPPLIER");
        intent.setTimeRange("ONE_MONTH");
        intent.setDepartment("ALL");

        for (String forbidden : new String[]{"部门", "浪费", "违规", "异常"}) {
            intent.setAnalysisGoal("哪个" + forbidden + "最严重");
            SummaryResult result = client.generateSummary(intent, "{}");
            assertFalse(result.isAiGenerated(),
                    "CANTEEN_SUPPLIER with '" + forbidden + "' should not be AI-generated");
            assertTrue(result.getText().contains("不能分析"),
                    "Response should explain the field boundary for: " + forbidden);
        }
    }

    // ── 4. LESSEE 敏感字段不得在 summary prompt 中原文出现 ──────────────
    @Test
    void deepSeekPrompts_doNotExposeRawSensitiveFields() throws Exception {
        JijianDeepSeekIntentClient client = new JijianDeepSeekIntentClient();
        String summaryPrompt = invokePromptMethod(client, "buildSummarySystemPrompt");

        assertTrue(summaryPrompt.contains("不得输出手机号"),
                "Summary prompt must forbid raw phone numbers");
        assertFalse(summaryPrompt.contains("ark-"),
                "Prompt must not contain any real API key fragment");
    }

    // ── 5. ATTENDANCE_DAILY: prompt 明确约束不直接输出违纪认定 ───────────
    @Test
    void attendanceDaily_summaryPrompt_restrictsViolationJudgement() throws Exception {
        JijianDeepSeekIntentClient client = new JijianDeepSeekIntentClient();
        String summaryPrompt = invokePromptMethod(client, "buildSummarySystemPrompt");

        assertTrue(summaryPrompt.contains("不得生成 SQL"),
                "Summary prompt must forbid SQL generation");
        JijianQueryFieldWhitelist whitelist = new JijianQueryFieldWhitelist();
        java.util.List<String> forbidden = whitelist.forbiddenAnalyses("ATTENDANCE_DAILY");
        assertNotNull(forbidden, "forbiddenAnalyses should never return null");
        assertFalse(forbidden.contains("打卡"),
                "打卡 is a valid analysis dimension and must not appear in forbiddenAnalyses");
    }

    // ── 6. DeepSeek API key 不得出现在 intent/summary prompt 中 ────────
    @Test
    void deepSeekSystemPrompts_doNotContainRealApiKey() throws Exception {
        JijianDeepSeekIntentClient client = new JijianDeepSeekIntentClient();
        String intentPrompt = invokePromptMethod(client, "buildIntentSystemPrompt");
        String summaryPrompt = invokePromptMethod(client, "buildSummarySystemPrompt");

        assertFalse(intentPrompt.contains("ark-"), "Intent prompt must not contain any API key");
        assertFalse(summaryPrompt.contains("ark-"), "Summary prompt must not contain any API key");
    }

    private String invokePromptMethod(JijianDeepSeekIntentClient client, String methodName) throws Exception {
        Method m = JijianDeepSeekIntentClient.class.getDeclaredMethod(methodName);
        m.setAccessible(true);
        return (String) m.invoke(client);
    }

    // ── 7. primary 的 formType 都有 supported=true ───────────────────────
    @Test
    void primaryFormTypes_areAllSupported() {
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                assertTrue(e.isSupported(),
                        "Primary formType must be supported: " + e.getValue());
            }
        }
    }
}
