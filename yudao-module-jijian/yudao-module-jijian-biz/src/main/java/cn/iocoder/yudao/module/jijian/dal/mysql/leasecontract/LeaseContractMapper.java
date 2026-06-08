package cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeaseContractMapper extends BaseMapperX<LeaseContractDO> {

    default List<LeaseContractDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeaseContractDO>()
                .eq(LeaseContractDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default List<LeaseContractDO> selectListForSummary(LocalDateTime startTime) {
        return selectList(new LambdaQueryWrapper<LeaseContractDO>()
                .ge(LeaseContractDO::getCreateTime, startTime));
    }

    default PageResult<LeaseContractDO> selectPageForQuery(PageParam pageParam, LocalDateTime startTime) {
        return selectPage(pageParam, new LambdaQueryWrapper<LeaseContractDO>()
                .ge(LeaseContractDO::getCreateTime, startTime)
                .orderByDesc(LeaseContractDO::getCreateTime));
    }

}
