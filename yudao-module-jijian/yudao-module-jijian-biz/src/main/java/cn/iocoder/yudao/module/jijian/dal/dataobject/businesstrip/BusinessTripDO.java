package cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 纪检出差 DO（含用户要求的全部业务字段） */
@TableName("jijian_business_trip")
@KeySequence("jijian_business_trip_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class BusinessTripDO extends TenantBaseDO {
    @TableId private Long id;

    /** 部门 */
    private String department;
    /** 申请人显示名（如 "张三(78001234)"）*/
    private String applicantName;
    /** 员工编号（从申请人显示名中解析或直接读取）*/
    private String employeeNo;

    /** 出差事由 */
    private String tripReason;
    /** 出发地 */
    private String departurePlace;
    /** 目的地 */
    private String destination;
    /** 出差开始日期 */
    private LocalDateTime startDate;
    /** 出差结束日期 */
    private LocalDateTime endDate;
    /** 出差天数 */
    private BigDecimal tripDays;
    /** 出差人员（逗号分隔）*/
    private String tripPersonnel;

    /** 是否出义（原文：出义/不出义/是/否） */
    private String isOutside;
    /** 出义地点 */
    private String outsideLocation;

    /** 备注 */
    private String remark;
    /** 来源解析数据 ID */
    private Long sourceParsedDataId;

    // ── 向后兼容旧字段（保留，不删，避免查询模块依赖旧列报错）──
    @Deprecated private String leaveType;
    @Deprecated private String leaveReason;
    @Deprecated private LocalDateTime startTime;
    @Deprecated private LocalDateTime endTime;
    @Deprecated private BigDecimal leaveDays;
    @Deprecated private String leaveStatus;
    @Deprecated private String leaveMonth;
}
