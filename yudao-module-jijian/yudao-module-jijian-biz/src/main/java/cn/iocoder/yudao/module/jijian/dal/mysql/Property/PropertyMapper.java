package cn.iocoder.yudao.module.jijian.dal.mysql.Property;

import java.time.LocalDateTime;
import java.util.*;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    // ========== P4 查询模块 ==========

    /**
     * 按时间范围分页查询房产记录。
     * jijian_property 表无 department 字段，故不支持部门过滤。
     * 时间范围以 create_time >= startTime 为条件。
     *
     * @param pageParam 分页参数（pageNo / pageSize），可传入任何 PageParam 子类
     * @param startTime create_time 起始时间（闭区间）
     */
    default PageResult<PropertyDO> selectPageForQuery(PageParam pageParam,
                                                       LocalDateTime startTime) {
        LambdaQueryWrapper<PropertyDO> wrapper = new LambdaQueryWrapper<PropertyDO>()
                .ge(PropertyDO::getCreateTime, startTime)
                .orderByDesc(PropertyDO::getCreateTime);
        return selectPage(pageParam, wrapper);
    }

    /**
     * 查询时间范围内的全部房产记录（用于聚合统计）。
     * 不含 source_parsed_data_id 过滤，仅按 create_time 过滤。
     */
    default List<PropertyDO> selectListForSummary(LocalDateTime startTime) {
        return selectList(new LambdaQueryWrapper<PropertyDO>()
                .ge(PropertyDO::getCreateTime, startTime));
    }

    /**
     * Fixed whitelist query for REAL_ESTATE aggregate/list usage.
     * The request currently contributes only timeRange/page semantics at service layer;
     * department is ignored because jijian_property has no department column.
     */
    default List<PropertyDO> selectListForQuery(JijianQueryPageReqVO req,
                                                LocalDateTime startTime) {
        return selectList(new LambdaQueryWrapper<PropertyDO>()
                .ge(PropertyDO::getCreateTime, startTime)
                .orderByDesc(PropertyDO::getCreateTime));
    }

}
