package cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeaseContractMapper extends BaseMapperX<LeaseContractDO> {

    default List<LeaseContractDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeaseContractDO>()
                .eq(LeaseContractDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
