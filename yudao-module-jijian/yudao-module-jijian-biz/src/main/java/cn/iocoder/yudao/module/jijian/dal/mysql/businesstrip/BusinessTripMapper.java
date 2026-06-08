package cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface BusinessTripMapper extends BaseMapperX<BusinessTripDO> {

    default List<BusinessTripDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<BusinessTripDO>()
                .eq(BusinessTripDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default PageResult<BusinessTripDO> selectPageForQuery(PageParam pageParam, String department, LocalDateTime startTime) {
        QueryWrapper<BusinessTripDO> wrapper = new QueryWrapper<BusinessTripDO>()
                .ge("create_time", startTime)
                .orderByDesc("create_time");
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectPage(pageParam, wrapper);
    }

    default List<BusinessTripDO> selectListForQuery(String department, LocalDateTime startTime) {
        QueryWrapper<BusinessTripDO> wrapper = new QueryWrapper<BusinessTripDO>()
                .ge("create_time", startTime);
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectList(wrapper);
    }

    default List<String> selectDistinctDepartments() {
        return selectObjs(new LambdaQueryWrapper<BusinessTripDO>()
                .select(BusinessTripDO::getDepartment)
                .isNotNull(BusinessTripDO::getDepartment)
                .ne(BusinessTripDO::getDepartment, "")
                .groupBy(BusinessTripDO::getDepartment)
                .orderByAsc(BusinessTripDO::getDepartment));
    }

    default List<BusinessTripDO> selectListInDateRange(LocalDateTime startTime, LocalDateTime endTime, String department) {
        LambdaQueryWrapper<BusinessTripDO> wrapper = new LambdaQueryWrapper<BusinessTripDO>()
                .lt(BusinessTripDO::getStartTime, endTime)
                .gt(BusinessTripDO::getEndTime, startTime);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(BusinessTripDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
