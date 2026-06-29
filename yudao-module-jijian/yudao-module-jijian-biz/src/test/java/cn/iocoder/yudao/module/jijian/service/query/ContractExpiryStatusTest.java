package cn.iocoder.yudao.module.jijian.service.query;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractExpiryStatusTest {

    @Test
    void endDateBeforeToday_isExpired() {
        LocalDate today = LocalDate.now();

        JijianAiSqlAgentService.ContractExpiryStatus status =
                JijianAiSqlAgentService.calculateContractExpiry(today, today.minusDays(64));

        assertTrue(status.expired);
        assertEquals(0L, status.remainingDays);
        assertEquals(64L, status.overdueDays);
    }

    @Test
    void endDateOnOrAfterToday_isValid() {
        LocalDate today = LocalDate.now();

        JijianAiSqlAgentService.ContractExpiryStatus todayStatus =
                JijianAiSqlAgentService.calculateContractExpiry(today, today);
        JijianAiSqlAgentService.ContractExpiryStatus futureStatus =
                JijianAiSqlAgentService.calculateContractExpiry(today, today.plusDays(10));

        assertFalse(todayStatus.expired);
        assertEquals(0L, todayStatus.remainingDays);
        assertFalse(futureStatus.expired);
        assertEquals(10L, futureStatus.remainingDays);
        assertEquals(0L, futureStatus.overdueDays);
    }
}
