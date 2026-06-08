package cn.iocoder.yudao.module.jijian.enums.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JijianQueryFormTypeEnum {

    PROPERTY_INFO("PROPERTY_INFO", "\u623f\u4ea7\u60c5\u51b5\u8868", true, true),
    LESSEE("LESSEE", "\u79df\u8d41\u4eba\u5458\u8868", true, true),
    LEASE_CONTRACT("LEASE_CONTRACT", "\u79df\u8d41\u5408\u540c\u8868", true, true),
    ATTENDANCE_DAILY("ATTENDANCE_DAILY", "\u8003\u52e4\u65e5\u62a5\u8868", true, true),
    RECUPERATION_LEAVE("RECUPERATION_LEAVE", "\u7597\u4f11\u517b\u8bf7\u5047\u8868", true, true),
    PERSONAL_LEAVE("PERSONAL_LEAVE", "\u4e8b\u5047\u8868", true, true),
    BUSINESS_TRIP("BUSINESS_TRIP", "\u51fa\u5dee\u8868", true, true),
    COMPENSATORY_LEAVE("COMPENSATORY_LEAVE", "\u8c03\u4f11\u8868", true, true),
    CANTEEN_SUPPLIER("CANTEEN_SUPPLIER", "\u98df\u5802\u4f9b\u5e94\u5546\u4fe1\u606f\u8868", true, true),

    ATTENDANCE("ATTENDANCE", "\u8003\u52e4\u4fe1\u606f\uff08\u517c\u5bb9\uff09", true, false),
    REAL_ESTATE("REAL_ESTATE", "\u623f\u5c4b\u51fa\u79df\u4fe1\u606f\uff08\u517c\u5bb9\uff09", true, false),
    CANTEEN_SUPPLY("CANTEEN_SUPPLY", "\u98df\u5802\u4f9b\u5e94\uff08\u517c\u5bb9\uff09", true, false),
    LEAVE_HEALTH("LEAVE_HEALTH", "\u7597\u4f11\u517b\u8bf7\u5047\uff08\u517c\u5bb9\uff09", true, false),
    LEAVE_PERSONAL("LEAVE_PERSONAL", "\u4e8b\u5047\u8bb0\u5f55\uff08\u517c\u5bb9\uff09", true, false),
    COMPENSATORY("COMPENSATORY", "\u8c03\u4f11\u8bb0\u5f55\uff08\u517c\u5bb9\uff09", true, false);

    private final String value;
    private final String label;
    private final boolean supported;
    private final boolean primary;

    public static JijianQueryFormTypeEnum of(String value) {
        for (JijianQueryFormTypeEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
