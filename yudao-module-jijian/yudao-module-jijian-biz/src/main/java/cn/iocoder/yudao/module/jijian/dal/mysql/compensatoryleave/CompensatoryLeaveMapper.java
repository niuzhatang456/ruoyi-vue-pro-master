package cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface CompensatoryLeaveMapper extends BaseMapperX<CompensatoryLeaveDO> {

    default List<CompensatoryLeaveDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<CompensatoryLeaveDO>()
                .eq(CompensatoryLeaveDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default PageResult<CompensatoryLeaveDO> selectPageForQuery(PageParam pageParam, String department, LocalDateTime startTime) {
        QueryWrapper<CompensatoryLeaveDO> wrapper = new QueryWrapper<CompensatoryLeaveDO>()
                .ge("create_time", startTime)
                .orderByDesc("create_time");
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectPage(pageParam, wrapper);
    }

    default List<CompensatoryLeaveDO> selectListForQuery(String department, LocalDateTime startTime) {
        QueryWrapper<CompensatoryLeaveDO> wrapper = new QueryWrapper<CompensatoryLeaveDO>()
                .ge("create_time", startTime);
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectList(wrapper);
    }

    default List<String> selectDistinctDepartments() {
        return selectObjs(new LambdaQueryWrapper<CompensatoryLeaveDO>()
                .select(CompensatoryLeaveDO::getDepartment)
                .isNotNull(CompensatoryLeaveDO::getDepartment)
                .ne(CompensatoryLeaveDO::getDepartment, "")
                .groupBy(CompensatoryLeaveDO::getDepartment)
                .orderByAsc(CompensatoryLeaveDO::getDepartment));
    }

    default List<CompensatoryLeaveDO> selectListInDateRange(LocalDateTime startTime, LocalDateTime endTime, String department) {
        LambdaQueryWrapper<CompensatoryLeaveDO> wrapper = new LambdaQueryWrapper<CompensatoryLeaveDO>()
                .lt(CompensatoryLeaveDO::getCompensatoryStartTime, endTime)
                .gt(CompensatoryLeaveDO::getCompensatoryEndTime, startTime);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(CompensatoryLeaveDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
