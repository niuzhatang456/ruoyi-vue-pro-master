package cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CanteenSupplierMapper extends BaseMapperX<CanteenSupplierDO> {

    default List<CanteenSupplierDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<CanteenSupplierDO>()
                .eq(CanteenSupplierDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
