package cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("jijian_import_parsed_data")
@KeySequence("jijian_import_parsed_data_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedDataDO extends TenantBaseDO {

    @TableId
    private Long id;

    /** 所属导入记录 ID */
    private Long importRecordId;

    /** 识别出的表单业务类型（来自 FormTypeConstants） */
    private String formType;

    /** OCR / 解析摘要文本 */
    private String rawText;

    /** 解析结构化数据（headers + rows JSON） */
    private String parsedJson;

    /** 识别置信度 */
    private BigDecimal confidence;

    /**
     * 解析状态（不含确认状态）
     * success → 解析成功，待确认写入
     * failed  → 解析失败
     */
    private String status;

    /**
     * 确认状态（独立于解析状态）
     * pending   → 待确认
     * confirmed → 已确认写入正式业务表
     */
    private String confirmStatus;

    /** 解析失败或确认失败时的错误信息 */
    private String errorMsg;

    /** 用户在前端校正后保存的结构化数据（覆盖 parsedJson 参与确认写入） */
    private String correctedJson;

    // ──────────── 确认写入追溯字段 ─────────────────────────────────────

    /**
     * 确认写入后的正式房产记录ID（单条房产记录兼容字段，通用场景查 businessIds）
     * @deprecated 请改用 businessIds
     */
    @Deprecated
    private Long confirmedPropertyId;

    /** 确认写入时间 */
    private LocalDateTime confirmTime;

    /** 确认写入操作人 ID */
    private Long confirmUserId;

    /** 写入的正式业务表名（如 jijian_property、jijian_canteen_supplier） */
    private String businessTable;

    /**
     * 写入的正式业务记录 ID 列表（JSON 数组，如 [1,2,3]）
     * 多行 Excel 写入多条记录时存储完整列表
     */
    private String businessIds;
}
