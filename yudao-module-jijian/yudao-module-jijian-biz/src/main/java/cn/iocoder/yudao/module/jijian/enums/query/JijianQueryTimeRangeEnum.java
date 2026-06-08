package cn.iocoder.yudao.module.jijian.enums.query;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@AllArgsConstructor
public enum JijianQueryTimeRangeEnum {

    ONE_DAY("ONE_DAY", "一日", 1),
    ONE_WEEK("ONE_WEEK", "一周", 7),
    ONE_MONTH("ONE_MONTH", "一个月", 30),
    THREE_MONTHS("THREE_MONTHS", "三个月", 90),
    HALF_YEAR("HALF_YEAR", "半年", 180),
    ONE_YEAR("ONE_YEAR", "一年", 365),
    ALL("ALL", "全部", 3650),
    ;

    private final String value;
    private final String label;
    /** 往前推天数；ALL = 3650 天（约10年），视为全量 */
    private final int days;

    public LocalDate startDate() {
        return LocalDate.now().minusDays(days);
    }

    public static JijianQueryTimeRangeEnum of(String value) {
        for (JijianQueryTimeRangeEnum e : values()) {
            if (e.value.equals(value)) {
                return e;
            }
        }
        return null;
    }
}
