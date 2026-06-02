package cn.iocoder.yudao.module.jijian.dal.mysql.Property;

import java.util.*;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import org.apache.ibatis.annotations.Mapper;
import cn.iocoder.yudao.module.jijian.controller.admin.Property.vo.*;

/**
 * 房产情况 Mapper
 *
 * @author admin
 */
@Mapper
public interface PropertyMapper extends BaseMapperX<PropertyDO> {

    default PageResult<PropertyDO> selectPage(PropertyPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<PropertyDO>()
                .eqIfPresent(PropertyDO::getPropertyAddress, reqVO.getPropertyAddress())
                .likeIfPresent(PropertyDO::getPropertyName, reqVO.getPropertyName())
                .eqIfPresent(PropertyDO::getOwnershipInfo, reqVO.getOwnershipInfo())
                .betweenIfPresent(PropertyDO::getBuildingTime, reqVO.getBuildingTime())
                .eqIfPresent(PropertyDO::getArea, reqVO.getArea())
                .eqIfPresent(PropertyDO::getLeaseStatus, reqVO.getLeaseStatus())
                .eqIfPresent(PropertyDO::getRemark, reqVO.getRemark())
                .betweenIfPresent(PropertyDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(PropertyDO::getId));
    }

    /** 根据来源解析数据ID查询房产记录（用于幂等判断） */
    default PropertyDO selectBySourceParsedDataId(Long sourceParsedDataId) {
        return selectOne(new LambdaQueryWrapperX<PropertyDO>()
                .eq(PropertyDO::getSourceParsedDataId, sourceParsedDataId));
    }

}