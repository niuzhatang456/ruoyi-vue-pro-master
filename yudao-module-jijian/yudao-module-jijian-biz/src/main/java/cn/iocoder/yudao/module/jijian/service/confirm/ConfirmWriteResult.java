package cn.iocoder.yudao.module.jijian.service.confirm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
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

    // ──────────── 导入统计字段 ────────────────────────────────────────────────

    /** 原始解析总行数（来自 parsedJson / correctedJson） */
    private Integer totalRows;

    /** 跳过行数（纯空白行 / 合计行，无有效业务字段） */
    private Integer skippedRows;

    /** 失败行数（有业务字段但关键字段缺失或解析异常） */
    private Integer failedRows;

    /** 跳过行原因列表（每行一条说明，最多 20 条） */
    private List<String> skippedMessages;

    /** 失败行原因列表（每行一条说明，最多 20 条） */
    private List<String> failedMessages;

    // ──────────── 工厂方法 ────────────────────────────────────────────────────

    /**
     * 不带统计数据的向后兼容方法（其他 7 类 Handler 继续使用）。
     * 新增字段默认为 0 / 空列表，不影响现有调用方。
     */
    public static ConfirmWriteResult of(String formType, String businessTable, List<Long> ids) {
        return ConfirmWriteResult.builder()
                .formType(formType)
                .businessTable(businessTable)
                .confirmedIds(ids)
                .confirmedCount(ids.size())
                .idempotent(false)
                .totalRows(ids.size())
                .skippedRows(0)
                .failedRows(0)
                .skippedMessages(Collections.emptyList())
                .failedMessages(Collections.emptyList())
                .build();
    }

    /** 向后兼容：没有 businessTable 的调用方 */
    public static ConfirmWriteResult of(String formType, List<Long> ids) {
        return of(formType, null, ids);
    }

    /**
     * 带完整统计数据的工厂方法（AttendanceDaily / CompensatoryLeave 等多行 Handler 使用）。
     *
     * @param totalRows       原始解析总行数
     * @param skippedRows     跳过行数（空白/合计行）
     * @param skippedMessages 跳过行说明（最多 20 条）
     * @param failedRows      失败行数（有业务字段但关键字段缺失或解析异常）
     * @param failedMessages  失败行说明（最多 20 条）
     */
    public static ConfirmWriteResult ofWithStats(
            String formType, String businessTable, List<Long> ids,
            int totalRows, int skippedRows, List<String> skippedMessages,
            int failedRows, List<String> failedMessages) {
        return ConfirmWriteResult.builder()
                .formType(formType)
                .businessTable(businessTable)
                .confirmedIds(ids)
                .confirmedCount(ids.size())
                .idempotent(false)
                .totalRows(totalRows)
                .skippedRows(skippedRows)
                .skippedMessages(skippedMessages != null ? skippedMessages : Collections.emptyList())
                .failedRows(failedRows)
                .failedMessages(failedMessages != null ? failedMessages : Collections.emptyList())
                .build();
    }

    public static ConfirmWriteResult idempotent(String formType, String businessTable, List<Long> ids) {
        return ConfirmWriteResult.builder()
                .formType(formType)
                .businessTable(businessTable)
                .confirmedIds(ids)
                .confirmedCount(ids.size())
                .idempotent(true)
                .totalRows(ids.size())
                .skippedRows(0)
                .failedRows(0)
                .skippedMessages(Collections.emptyList())
                .failedMessages(Collections.emptyList())
                .build();
    }

    public static ConfirmWriteResult idempotent(String formType, List<Long> ids) {
        return idempotent(formType, null, ids);
    }
}
