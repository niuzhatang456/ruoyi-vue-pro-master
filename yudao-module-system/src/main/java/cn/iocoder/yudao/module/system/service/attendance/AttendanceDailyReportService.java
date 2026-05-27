package cn.iocoder.yudao.module.system.service.attendance;

import cn.iocoder.yudao.module.system.dal.dataobject.attendance.AttendanceDailyReportDO;
import cn.iocoder.yudao.module.system.service.ai.bo.AttendanceQueryConditionsBO;

import java.util.List;

/**
 * 考勤日报表 Service
 */
public interface AttendanceDailyReportService {

    /**
     * 按解析条件查询考勤日报
     */
    List<AttendanceDailyReportDO> getListByConditions(AttendanceQueryConditionsBO conditions);

}
