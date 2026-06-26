package cn.iocoder.yudao.module.jijian.dal.dataobject.operationauditlog;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@TableName("jijian_operation_audit_log")
@KeySequence("jijian_operation_audit_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class OperationAuditLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    private Long userId;
    private String userName;
    private String operationType;
    private String moduleName;
    private String targetTable;
    private String targetId;
    private String requestSummary;
    private String resultSummary;
    private Boolean success;
    private String errorMessage;
    private String clientIp;
}
