package cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface BusinessTripMapper extends BaseMapperX<BusinessTripDO> {

    default List<BusinessTripDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<BusinessTripDO>()
                .eq(BusinessTripDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
