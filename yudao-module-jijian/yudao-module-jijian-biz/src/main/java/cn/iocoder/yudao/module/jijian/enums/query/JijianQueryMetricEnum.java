package cn.iocoder.yudao.module.jijian.enums.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JijianQueryMetricEnum {

    TOTAL("TOTAL", "总计"),
    BY_DEPARTMENT("BY_DEPARTMENT", "按部门统计"),
    BY_ATTENDANCE_STATUS("BY_ATTENDANCE_STATUS", "按出勤状态统计"),
    DETAIL_SAMPLE("DETAIL_SAMPLE", "明细样本"),
    ;

    private final String value;
    private final String label;

    public static JijianQueryMetricEnum of(String value) {
        for (JijianQueryMetricEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
