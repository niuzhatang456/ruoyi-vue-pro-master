package cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LeaveHealthMapper extends BaseMapperX<LeaveHealthDO> {

    default List<LeaveHealthDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeaveHealthDO>()
                .eq(LeaveHealthDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default PageResult<LeaveHealthDO> selectPageForQuery(PageParam pageParam, String department, LocalDateTime startTime) {
        QueryWrapper<LeaveHealthDO> wrapper = new QueryWrapper<LeaveHealthDO>()
                .ge("create_time", startTime)
                .orderByDesc("create_time");
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectPage(pageParam, wrapper);
    }

    default List<LeaveHealthDO> selectListForQuery(String department, LocalDateTime startTime) {
        QueryWrapper<LeaveHealthDO> wrapper = new QueryWrapper<LeaveHealthDO>()
                .ge("create_time", startTime);
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectList(wrapper);
    }

    default List<String> selectDistinctDepartments() {
        return selectObjs(new LambdaQueryWrapper<LeaveHealthDO>()
                .select(LeaveHealthDO::getDepartment)
                .isNotNull(LeaveHealthDO::getDepartment)
                .ne(LeaveHealthDO::getDepartment, "")
                .groupBy(LeaveHealthDO::getDepartment)
                .orderByAsc(LeaveHealthDO::getDepartment));
    }

    default List<LeaveHealthDO> selectListInDateRange(LocalDateTime startTime, LocalDateTime endTime, String department) {
        LambdaQueryWrapper<LeaveHealthDO> wrapper = new LambdaQueryWrapper<LeaveHealthDO>()
                .lt(LeaveHealthDO::getStartTime, endTime)
                .gt(LeaveHealthDO::getEndTime, startTime);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(LeaveHealthDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
