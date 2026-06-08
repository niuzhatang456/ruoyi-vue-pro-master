package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandlerRegistryTest {

    private JijianFormQueryHandlerRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        List<JijianFormQueryHandler> handlers = new ArrayList<>();
        for (JijianQueryFormTypeEnum e : JijianQueryFormTypeEnum.values()) {
            if (e.isPrimary()) {
                handlers.add(fakeHandler(e));
            }
        }
        registry = new JijianFormQueryHandlerRegistry();
        Field field = JijianFormQueryHandlerRegistry.class.getDeclaredField("handlers");
        field.setAccessible(true);
        field.set(registry, handlers);
        registry.init();
    }

    @Test
    void ninePrimaryHandlers_registeredAndSupported() {
        List<String> supported = registry.listSupportedFormTypes();
        assertEquals(9, supported.size());
        assertTrue(supported.contains("PROPERTY_INFO"));
        assertTrue(supported.contains("LESSEE"));
        assertTrue(supported.contains("LEASE_CONTRACT"));
        assertTrue(supported.contains("ATTENDANCE_DAILY"));
        assertTrue(supported.contains("RECUPERATION_LEAVE"));
        assertTrue(supported.contains("PERSONAL_LEAVE"));
        assertTrue(supported.contains("BUSINESS_TRIP"));
        assertTrue(supported.contains("COMPENSATORY_LEAVE"));
        assertTrue(supported.contains("CANTEEN_SUPPLIER"));
    }

    @Test
    void unknownFormType_returnsNull() {
        assertNull(registry.getHandlerOrNull("UNKNOWN_TYPE"));
        assertNull(registry.getHandlerOrNull(null));
    }

    @Test
    void eachPrimarySummary_returnsNonNullMap() {
        for (String formType : registry.listSupportedFormTypes()) {
            JijianAiQueryIntent intent = new JijianAiQueryIntent();
            intent.setFormType(formType);
            Object summary = registry.getRequiredHandler(formType).summaryByIntent(intent);
            assertNotNull(summary);
        }
    }

    @Test
    void getRequiredHandler_unknownType_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> registry.getRequiredHandler("NO_SUCH_TYPE"));
    }

    private JijianFormQueryHandler fakeHandler(JijianQueryFormTypeEnum type) {
        return new JijianFormQueryHandler() {
            @Override
            public JijianQueryFormTypeEnum getFormType() {
                return type;
            }

            @Override
            public boolean isSupported() {
                return true;
            }

            @Override
            public List<JijianQueryDepartmentVO> getDepartments() {
                return Collections.singletonList(new JijianQueryDepartmentVO("ALL", "ALL"));
            }

            @Override
            public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
                return new JijianQueryPageRespVO();
            }

            @Override
            public Object summaryByIntent(JijianAiQueryIntent intent) {
                return Collections.singletonMap("formType", type.getValue());
            }
        };
    }
}
