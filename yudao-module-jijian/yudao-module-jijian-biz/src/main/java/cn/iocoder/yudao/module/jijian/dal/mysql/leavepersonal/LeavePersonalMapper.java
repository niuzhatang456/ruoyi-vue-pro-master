package cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeavePersonalMapper extends BaseMapperX<LeavePersonalDO> {

    default List<LeavePersonalDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeavePersonalDO>()
                .eq(LeavePersonalDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default PageResult<LeavePersonalDO> selectPageForQuery(PageParam pageParam, String department, LocalDateTime startTime) {
        QueryWrapper<LeavePersonalDO> wrapper = new QueryWrapper<LeavePersonalDO>()
                .ge("create_time", startTime)
                .orderByDesc("create_time");
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectPage(pageParam, wrapper);
    }

    default List<LeavePersonalDO> selectListForQuery(String department, LocalDateTime startTime) {
        QueryWrapper<LeavePersonalDO> wrapper = new QueryWrapper<LeavePersonalDO>()
                .ge("create_time", startTime);
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectList(wrapper);
    }

    default List<String> selectDistinctDepartments() {
        return selectObjs(new LambdaQueryWrapper<LeavePersonalDO>()
                .select(LeavePersonalDO::getDepartment)
                .isNotNull(LeavePersonalDO::getDepartment)
                .ne(LeavePersonalDO::getDepartment, "")
                .groupBy(LeavePersonalDO::getDepartment)
                .orderByAsc(LeavePersonalDO::getDepartment));
    }

    default List<LeavePersonalDO> selectListInDateRange(LocalDateTime startTime, LocalDateTime endTime, String department) {
        LambdaQueryWrapper<LeavePersonalDO> wrapper = new LambdaQueryWrapper<LeavePersonalDO>()
                .lt(LeavePersonalDO::getStartTime, endTime)
                .gt(LeavePersonalDO::getEndTime, startTime);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(LeavePersonalDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
