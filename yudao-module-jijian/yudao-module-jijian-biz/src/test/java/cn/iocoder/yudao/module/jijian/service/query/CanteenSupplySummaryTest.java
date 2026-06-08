package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import cn.iocoder.yudao.module.jijian.service.query.ai.IntentResult;
import cn.iocoder.yudao.module.jijian.service.query.ai.LocalFallbackAiIntentClient;
import cn.iocoder.yudao.module.jijian.service.query.ai.SummaryResult;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CanteenSupplySummaryTest {

    @Test
    void sameProjectSpecUnit_differentPrices_buildsPriceVariance() throws Exception {
        Map<String, Object> summary = canteenSummary(Arrays.asList(
                supplier("土豆", "一级", "斤", "2.00", "采价点A", "异常浪费"),
                supplier("土豆", "一级", "斤", "2.80", "采价点B", "违规浪费"),
                supplier("白菜", "一级", "斤", "1.50", "采价点A", "正常")
        ));

        assertEquals(3, summary.get("totalCount"));
        assertEquals(2, summary.get("totalProjectCount"));
        assertEquals(1L, summary.get("priceVarianceCount"));

        List<Map<String, Object>> items = (List<Map<String, Object>>) summary.get("priceVarianceItems");
        Map<String, Object> first = items.get(0);
        assertEquals("土豆", first.get("projectName"));
        assertEquals(new BigDecimal("2.00"), first.get("minPrice"));
        assertEquals(new BigDecimal("2.80"), first.get("maxPrice"));
        assertEquals(new BigDecimal("0.80"), first.get("priceDiff"));
        assertFalse(first.containsKey("remark"));
    }

    @Test
    void differentPurchasePoints_areGroupedWithoutDepartmentOrWasteFields() throws Exception {
        Map<String, Object> summary = canteenSummary(Arrays.asList(
                supplier("土豆", "一级", "斤", "2.00", "采价点A", "某部门浪费严重"),
                supplier("土豆", "一级", "斤", "2.80", "采价点B", "某部门违规")
        ));

        List<Map<String, Object>> byPurchasePoint = (List<Map<String, Object>>) summary.get("byPurchasePoint");
        assertEquals(2, byPurchasePoint.size());
        assertFalse(summary.containsKey("byDepartment"));
        assertFalse(summary.containsKey("abnormalCount"));
        assertFalse(summary.containsKey("wasteCount"));
    }

    @Test
    void canteenForbiddenQuestion_isRejectedByLocalFallback() {
        LocalFallbackAiIntentClient client = new LocalFallbackAiIntentClient();
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("CANTEEN_SUPPLIER");
        intent.setDepartment("ALL");
        intent.setTimeRange("ONE_MONTH");
        intent.setAnalysisGoal("哪个部门食堂浪费最严重");

        SummaryResult result = client.generateSummary(intent, "{}");

        assertFalse(result.isAiGenerated());
        assertTrue(result.getText().contains("不包含部门"));
        assertTrue(result.getText().contains("不包含") && result.getText().contains("浪费"));
    }

    @Test
    void noApiKeyLocalFallback_isNotAiGenerated() {
        LocalFallbackAiIntentClient client = new LocalFallbackAiIntentClient();
        cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO req =
                new cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO();
        req.setFormType("CANTEEN_SUPPLIER");
        req.setDepartment("ALL");
        req.setTimeRange("ONE_MONTH");
        req.setMessage("分析不同采价点价格差异");

        IntentResult result = client.parseIntent(req);

        assertFalse(result.isAiGenerated());
        assertEquals("CANTEEN_SUPPLIER", result.getIntent().getFormType());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> canteenSummary(List<CanteenSupplierDO> data) throws Exception {
        JijianActualTableQueryService service = new JijianActualTableQueryService();
        Method method = JijianActualTableQueryService.class.getDeclaredMethod("canteenSupplierSummary", List.class);
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(service, data);
    }

    private CanteenSupplierDO supplier(String itemName, String specLevel, String unit,
                                      String price, String purchasePoint, String remark) {
        return CanteenSupplierDO.builder()
                .itemName(itemName)
                .specLevel(specLevel)
                .unit(unit)
                .price(new BigDecimal(price))
                .purchasePoint(purchasePoint)
                .remark(remark)
                .build();
    }
}
