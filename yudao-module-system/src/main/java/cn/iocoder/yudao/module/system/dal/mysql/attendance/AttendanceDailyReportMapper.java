package cn.iocoder.yudao.module.system.dal.mysql.attendance;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.attendance.AttendanceDailyReportDO;
import cn.iocoder.yudao.module.system.service.ai.bo.AttendanceQueryConditionsBO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AttendanceDailyReportMapper extends BaseMapperX<AttendanceDailyReportDO> {

    default List<AttendanceDailyReportDO> selectListByConditions(AttendanceQueryConditionsBO conditions) {
        LambdaQueryWrapperX<AttendanceDailyReportDO> wrapper = new LambdaQueryWrapperX<AttendanceDailyReportDO>()
                .eqIfPresent(AttendanceDailyReportDO::getEmployeeName, conditions.getEmployeeName())
                .eqIfPresent(AttendanceDailyReportDO::getEmployeeNo, conditions.getEmployeeNo())
                .eqIfPresent(AttendanceDailyReportDO::getDeptName, conditions.getDeptName())
                .likeIfPresent(AttendanceDailyReportDO::getClockInResult, conditions.getClockInResultLike())
                .likeIfPresent(AttendanceDailyReportDO::getClockOutResult, conditions.getClockOutResultLike())
                .betweenIfPresent(AttendanceDailyReportDO::getAttendanceDate,
                        conditions.getDateStart(), conditions.getDateEnd())
                .orderByDesc(AttendanceDailyReportDO::getAttendanceDate)
                .orderByDesc(AttendanceDailyReportDO::getId);
        if (StrUtil.isNotBlank(conditions.getRemarkKeyword())) {
            String keyword = conditions.getRemarkKeyword();
            wrapper.and(w -> w.like(AttendanceDailyReportDO::getClockInRemark, keyword)
                    .or().like(AttendanceDailyReportDO::getClockOutRemark, keyword)
                    .or().like(AttendanceDailyReportDO::getClockInLocation, keyword)
                    .or().like(AttendanceDailyReportDO::getClockOutLocation, keyword));
        }
        return selectList(wrapper);
    }

}
