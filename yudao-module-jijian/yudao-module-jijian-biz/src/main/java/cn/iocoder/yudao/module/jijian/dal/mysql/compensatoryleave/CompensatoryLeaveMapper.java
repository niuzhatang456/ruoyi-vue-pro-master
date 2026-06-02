package cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CompensatoryLeaveMapper extends BaseMapperX<CompensatoryLeaveDO> {

    default List<CompensatoryLeaveDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<CompensatoryLeaveDO>()
                .eq(CompensatoryLeaveDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
