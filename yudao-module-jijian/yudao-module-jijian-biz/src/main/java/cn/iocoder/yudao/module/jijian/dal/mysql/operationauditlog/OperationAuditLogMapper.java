package cn.iocoder.yudao.module.jijian.dal.mysql.operationauditlog;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.operationauditlog.OperationAuditLogDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditLogMapper extends BaseMapperX<OperationAuditLogDO> {
}
