package cn.iocoder.yudao.module.jijian.service.confirm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 ConfirmWriteResult 统计字段（无需数据库）。
 * 测试点：
 *   1. ofWithStats 工厂方法字段赋值正确
 *   2. 向后兼容 of() 方法默认值正确
 *   3. skippedRows + failedRows + confirmedCount == totalRows（正常场景）
 */
class ConfirmWriteResultStatsTest {

    @Test
    void ofWithStats_fieldsCorrect() {
        List<Long> ids = Arrays.asList(1L, 2L, 3L);
        List<String> skippedMsgs = Arrays.asList("第 5 行：空白行，已跳过", "第 10 行：合计行，已跳过");
        List<String> failedMsgs  = Collections.singletonList("第 8 行：姓名为空，但存在考勤字段");

        ConfirmWriteResult result = ConfirmWriteResult.ofWithStats(
                "ATTENDANCE", "jijian_attendance_daily", ids,
                /* totalRows */ 6,
                /* skippedRows */ 2, skippedMsgs,
                /* failedRows  */ 1, failedMsgs);

        assertEquals("ATTENDANCE",              result.getFormType());
        assertEquals("jijian_attendance_daily", result.getBusinessTable());
        assertEquals(3,                          result.getConfirmedCount());
        assertFalse(result.isIdempotent());

        assertEquals(6, result.getTotalRows());
        assertEquals(2, result.getSkippedRows());
        assertEquals(1, result.getFailedRows());
        assertEquals(skippedMsgs, result.getSkippedMessages());
        assertEquals(failedMsgs,  result.getFailedMessages());

        // skippedRows + failedRows + confirmedCount == totalRows
        assertEquals(
                result.getTotalRows(),
                result.getSkippedRows() + result.getFailedRows() + result.getConfirmedCount(),
                "总行数应等于跳过+失败+成功之和");
    }

    @Test
    void of_backwardCompat_defaultStats() {
        List<Long> ids = Arrays.asList(10L, 20L);
        ConfirmWriteResult result = ConfirmWriteResult.of("PROPERTY", "jijian_property", ids);

        // 新字段有默认值，不会 NPE
        assertNotNull(result.getSkippedMessages());
        assertNotNull(result.getFailedMessages());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getFailedRows());
        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getConfirmedCount());
        assertFalse(result.isIdempotent());
    }

    @Test
    void idempotent_defaultStats() {
        List<Long> ids = Collections.singletonList(99L);
        ConfirmWriteResult result = ConfirmWriteResult.idempotent("CANTEEN", "jijian_canteen_supplier", ids);

        assertTrue(result.isIdempotent());
        assertEquals(0, result.getSkippedRows());
        assertEquals(0, result.getFailedRows());
        assertNotNull(result.getSkippedMessages());
        assertNotNull(result.getFailedMessages());
    }
}
