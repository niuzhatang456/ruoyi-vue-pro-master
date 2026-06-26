package cn.iocoder.yudao.module.jijian.dal.mysql.canteenmarketprice;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteenmarketprice.CanteenMarketPriceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CanteenMarketPriceMapper extends BaseMapperX<CanteenMarketPriceDO> {

    default List<CanteenMarketPriceDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<CanteenMarketPriceDO>()
                .eq(CanteenMarketPriceDO::getSourceParsedDataId, sourceParsedDataId));
    }
}
