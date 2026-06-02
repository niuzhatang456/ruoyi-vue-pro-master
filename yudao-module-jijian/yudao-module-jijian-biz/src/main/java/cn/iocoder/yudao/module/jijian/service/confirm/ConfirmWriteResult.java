package cn.iocoder.yudao.module.jijian.service.confirm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** 确认写入结果 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmWriteResult {

    /** 业务类型（来自 FormTypeConstants） */
    private String formType;

    /** 写入的正式业务表名（如 jijian_property） */
    private String businessTable;

    /** 成功写入的正式业务记录 ID 列表 */
    private List<Long> confirmedIds;

    /** 成功写入数量 */
    private int confirmedCount;

    /** 是否幂等（已确认过，直接返回已有 ID） */
    private boolean idempotent;

    public static ConfirmWriteResult of(String formType, String businessTable, List<Long> ids) {
        return ConfirmWriteResult.builder()
                .formType(formType)
                .businessTable(businessTable)
                .confirmedIds(ids)
                .confirmedCount(ids.size())
                .idempotent(false)
                .build();
    }

    /** 向后兼容：没有 businessTable 的调用方 */
    public static ConfirmWriteResult of(String formType, List<Long> ids) {
        return of(formType, null, ids);
    }

    public static ConfirmWriteResult idempotent(String formType, String businessTable, List<Long> ids) {
        return ConfirmWriteResult.builder()
                .formType(formType)
                .businessTable(businessTable)
                .confirmedIds(ids)
                .confirmedCount(ids.size())
                .idempotent(true)
                .build();
    }

    public static ConfirmWriteResult idempotent(String formType, List<Long> ids) {
        return idempotent(formType, null, ids);
    }
}
