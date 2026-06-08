package cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CanteenSupplierMapper extends BaseMapperX<CanteenSupplierDO> {

    default List<CanteenSupplierDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<CanteenSupplierDO>()
                .eq(CanteenSupplierDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default PageResult<CanteenSupplierDO> selectPageForQuery(PageParam pageParam,
                                                             LocalDateTime startTime) {
        return selectPage(pageParam, new LambdaQueryWrapper<CanteenSupplierDO>()
                .ge(CanteenSupplierDO::getCreateTime, startTime)
                .orderByDesc(CanteenSupplierDO::getCreateTime));
    }

    default List<CanteenSupplierDO> selectListForQuery(LocalDateTime startTime) {
        return selectList(new LambdaQueryWrapper<CanteenSupplierDO>()
                .ge(CanteenSupplierDO::getCreateTime, startTime)
                .orderByDesc(CanteenSupplierDO::getCreateTime));
    }

}
