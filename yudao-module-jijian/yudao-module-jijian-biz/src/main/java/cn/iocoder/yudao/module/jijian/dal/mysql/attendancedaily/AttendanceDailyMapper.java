package cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        LambdaQueryWrapper<AttendanceDailyDO> wrapper = new LambdaQueryWrapper<AttendanceDailyDO>()
                .ge(AttendanceDailyDO::getAttendanceDate, startDate)
                .orderByDesc(AttendanceDailyDO::getAttendanceDate);
        if (req.getDepartment() != null && !"ALL".equals(req.getDepartment())) {
            wrapper.eq(AttendanceDailyDO::getDepartment, req.getDepartment());
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
        LambdaQueryWrapper<AttendanceDailyDO> wrapper = new LambdaQueryWrapper<AttendanceDailyDO>()
                .ge(AttendanceDailyDO::getAttendanceDate, startDate);
        if (department != null && !"ALL".equals(department)) {
            wrapper.eq(AttendanceDailyDO::getDepartment, department);
        }
        return selectList(wrapper);
    }

}
