package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class JijianFormTypeAutoDetectServiceTest {

    private final JijianFormTypeAutoDetectService service = new JijianFormTypeAutoDetectService();

    @Test
    void shouldStrongMatchCanteenBySevenCoreHeaders() {
        // 文件名/Sheet 名均无食堂关键词，仅靠 7 个核心表头命中 ≥5 个
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "scan_0403.xlsx", "Sheet1",
                Arrays.asList("序号", "商品名称", "规格", "单位", "数量", "单价", "小计", "备注"));

        assertEquals(FormTypeConstants.CANTEEN, result.detectedFormType);
        assertTrue(result.confidence >= 0.7, "强匹配置信度应不低于 0.7，实际=" + result.confidence);
        assertFalse(result.needsConfirmation);
    }

    @Test
    void shouldStrongMatchCanteenByNameQuantityPriceSubtotal() {
        // 命中 商品名称、数量、单价、小计 四项即可强匹配
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "未命名.png", null,
                Arrays.asList("商品名称", "数量", "单价", "小计"));

        assertEquals(FormTypeConstants.CANTEEN, result.detectedFormType);
        assertTrue(result.confidence >= 0.7);
        assertFalse(result.needsConfirmation);
    }

    @Test
    void shouldNotStrongMatchCanteenForAttendanceHeaders() {
        // 考勤表头不应被食堂强匹配影响
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "考勤日报.xlsx", "考勤",
                Arrays.asList("姓名", "部门", "日期", "打卡时间", "打卡结果", "打卡地点"));

        assertEquals(FormTypeConstants.ATTENDANCE, result.detectedFormType);
    }

    @Test
    void shouldAcceptAttendanceDailyWhenHalfHeadersMatched() {
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "日报.xlsx", "Sheet1",
                Arrays.asList("姓名", "员工编号", "部门", "日期", "星期", "上班打卡时间", "上班打卡结果"));

        assertEquals(FormTypeConstants.ATTENDANCE, result.detectedFormType);
        assertTrue(result.confidence >= 0.7);
        assertFalse(result.needsConfirmation);
    }

    @Test
    void shouldPreferMarketPriceWhenPricePointHeaderExists() {
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "义乌市民生商品市场零售价格信息公告.xlsx", "Sheet1",
                Arrays.asList("项目名称", "规格/等级", "单位", "价格", "采价点"));

        assertEquals(FormTypeConstants.CANTEEN_MARKET_PRICE, result.detectedFormType);
        assertNotEquals(FormTypeConstants.CANTEEN, result.detectedFormType);
    }

    @Test
    void shouldNotMatchCanteenWithoutHeaders() {
        JijianFormTypeAutoDetectService.DetectResult result = service.detect(
                "random.txt", null, Collections.emptyList());
        assertNotEquals(FormTypeConstants.CANTEEN, result.detectedFormType);
    }
}
