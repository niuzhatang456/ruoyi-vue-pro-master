package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.AttendanceSummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for P3 query handler registry routing.
 */
class HandlerRegistryTest {

    private JijianFormQueryHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        JijianFormQueryHandler attendanceHandler = new JijianFormQueryHandler() {
            @Override
            public JijianQueryFormTypeEnum getFormType() {
                return JijianQueryFormTypeEnum.ATTENDANCE;
            }

            @Override
            public boolean isSupported() {
                return true;
            }

            @Override
            public List<JijianQueryDepartmentVO> getDepartments() {
                return List.of(new JijianQueryDepartmentVO("ALL", "ALL"));
            }

            @Override
            public JijianAttendancePageRespVO pageQuery(JijianAttendancePageReqVO req) {
                return new JijianAttendancePageRespVO();
            }

            @Override
            public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
                return new JijianQueryPageRespVO();
            }

            @Override
            public Object summaryByIntent(JijianAiQueryIntent intent) {
                AttendanceSummaryDTO dto = new AttendanceSummaryDTO();
                dto.setTotalCount(42);
                return dto;
            }
        };

        JijianFormQueryHandler canteenHandler = new JijianFormQueryHandler() {
            @Override
            public JijianQueryFormTypeEnum getFormType() {
                return JijianQueryFormTypeEnum.CANTEEN_SUPPLY;
            }

            @Override
            public boolean isSupported() {
                return false;
            }

            @Override
            public List<JijianQueryDepartmentVO> getDepartments() {
                return List.of();
            }

            @Override
            public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
                throw new UnsupportedOperationException("CANTEEN_SUPPLY not yet supported");
            }

            @Override
            public Object summaryByIntent(JijianAiQueryIntent intent) {
                throw new UnsupportedOperationException("CANTEEN_SUPPLY not yet supported");
            }
        };

        registry = new JijianFormQueryHandlerRegistry();
        try {
            java.lang.reflect.Field field = JijianFormQueryHandlerRegistry.class.getDeclaredField("handlers");
            field.setAccessible(true);
            field.set(registry, Arrays.asList(attendanceHandler, canteenHandler));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        registry.init();
    }

    @Test
    void attendanceHandler_registeredAndSupported() {
        JijianFormQueryHandler h = registry.getHandlerOrNull("ATTENDANCE");
        assertNotNull(h);
        assertTrue(h.isSupported());
        assertEquals(JijianQueryFormTypeEnum.ATTENDANCE, h.getFormType());
    }

    @Test
    void realEstateHandler_notRegisteredForP3() {
        assertNull(registry.getHandlerOrNull("REAL_ESTATE"));
    }

    @Test
    void canteenHandler_registeredButNotSupported() {
        JijianFormQueryHandler h = registry.getHandlerOrNull("CANTEEN_SUPPLY");
        assertNotNull(h);
        assertFalse(h.isSupported());
    }

    @Test
    void unknownFormType_returnsNull() {
        assertNull(registry.getHandlerOrNull("UNKNOWN_TYPE"));
        assertNull(registry.getHandlerOrNull(null));
    }

    @Test
    void isSupported_attendanceOnly() {
        assertTrue(registry.isSupported("ATTENDANCE"));
        assertFalse(registry.isSupported("REAL_ESTATE"));
        assertFalse(registry.isSupported("CANTEEN_SUPPLY"));
        assertFalse(registry.isSupported("BUSINESS_TRIP"));
    }

    @Test
    void listSupportedFormTypes_attendanceOnly() {
        List<String> supported = registry.listSupportedFormTypes();
        assertEquals(1, supported.size());
        assertTrue(supported.contains("ATTENDANCE"));
    }

    @Test
    void attendanceSummaryByIntent_returnsAttendanceSummaryDTO() {
        JijianFormQueryHandler h = registry.getRequiredHandler("ATTENDANCE");
        JijianAiQueryIntent intent = new JijianAiQueryIntent();
        intent.setFormType("ATTENDANCE");
        intent.setDepartment("ALL");
        intent.setTimeRange("ONE_WEEK");
        Object result = h.summaryByIntent(intent);
        assertInstanceOf(AttendanceSummaryDTO.class, result);
        assertEquals(42, ((AttendanceSummaryDTO) result).getTotalCount());
    }

    @Test
    void getRequiredHandler_unknownType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> registry.getRequiredHandler("NO_SUCH_TYPE"));
    }

    @Test
    void unsupportedType_controllerPattern_noException() {
        JijianFormQueryHandler h = registry.getHandlerOrNull("CANTEEN_SUPPLY");
        boolean shouldReturnFriendlyFallback = (h == null || !h.isSupported());
        assertTrue(shouldReturnFriendlyFallback);
    }
}
