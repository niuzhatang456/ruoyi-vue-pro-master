package cn.iocoder.yudao.module.jijian.enums.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 纪检查询模块 - 数据类型枚举
 * TODO: 后续统一从 FormTypeConstants + ConfirmWriteHandlerRegistry 自动映射
 */
@Getter
@AllArgsConstructor
public enum JijianQueryFormTypeEnum {

    ATTENDANCE("ATTENDANCE", "考勤信息", true),
    REAL_ESTATE("REAL_ESTATE", "房产情况", false),
    CANTEEN_SUPPLY("CANTEEN_SUPPLY", "食堂供应", false),
    LESSEE("LESSEE", "租赁人员", false),
    LEASE_CONTRACT("LEASE_CONTRACT", "租赁合同", false),
    LEAVE_HEALTH("LEAVE_HEALTH", "疗休养假", false),
    LEAVE_PERSONAL("LEAVE_PERSONAL", "事假记录", false),
    BUSINESS_TRIP("BUSINESS_TRIP", "出差记录", false),
    COMPENSATORY("COMPENSATORY", "调休记录", false),
    ;

    private final String value;
    private final String label;
    /** 当前是否支持查询，只有 ATTENDANCE = true */
    private final boolean supported;

    public static JijianQueryFormTypeEnum of(String value) {
        for (JijianQueryFormTypeEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
