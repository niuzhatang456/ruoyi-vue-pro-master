package cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface AttendanceDailyMapper extends BaseMapperX<AttendanceDailyDO> {

    default List<AttendanceDailyDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<AttendanceDailyDO>()
                .eq(AttendanceDailyDO::getSourceParsedDataId, sourceParsedDataId));
    }

    // ========== P3 查询模块 ==========

    default PageResult<AttendanceDailyDO> selectPageForQuery(JijianAttendancePageReqVO req,
                                                              LocalDate startDate) {
        QueryWrapper<AttendanceDailyDO> wrapper = new QueryWrapper<AttendanceDailyDO>()
                .ge("create_time", startDate.atStartOfDay())
                .orderByDesc("create_time");
        String dept = req.getDepartment();
        if (dept != null && !"ALL".equals(dept) && !dept.isEmpty()) {
            wrapper.eq("department", dept);
        }
        return selectPage(req, wrapper);
    }

    default List<String> selectDistinctDepartments() {
        return selectObjs(new LambdaQueryWrapper<AttendanceDailyDO>()
                .select(AttendanceDailyDO::getDepartment)
                .isNotNull(AttendanceDailyDO::getDepartment)
                .ne(AttendanceDailyDO::getDepartment, "")
                .groupBy(AttendanceDailyDO::getDepartment)
                .orderByAsc(AttendanceDailyDO::getDepartment));
    }

    default List<AttendanceDailyDO> selectListForSummary(String department, LocalDate startDate) {
        QueryWrapper<AttendanceDailyDO> wrapper = new QueryWrapper<AttendanceDailyDO>()
                .ge("create_time", startDate.atStartOfDay());
        if (department != null && !"ALL".equals(department) && !department.isEmpty()) {
            wrapper.eq("department", department);
        }
        return selectList(wrapper);
    }

    default List<AttendanceDailyDO> selectListInDateRange(LocalDate startDate, LocalDate endDate, String department) {
        LambdaQueryWrapper<AttendanceDailyDO> wrapper = new LambdaQueryWrapper<AttendanceDailyDO>()
                .ge(AttendanceDailyDO::getAttendanceDate, startDate)
                .le(AttendanceDailyDO::getAttendanceDate, endDate)
                .orderByAsc(AttendanceDailyDO::getAttendanceDate);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(AttendanceDailyDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
