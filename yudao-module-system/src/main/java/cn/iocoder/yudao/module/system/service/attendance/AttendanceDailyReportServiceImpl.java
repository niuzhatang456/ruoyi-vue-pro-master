package cn.iocoder.yudao.module.system.service.attendance;

import cn.iocoder.yudao.module.system.dal.dataobject.attendance.AttendanceDailyReportDO;
import cn.iocoder.yudao.module.system.dal.mysql.attendance.AttendanceDailyReportMapper;
import cn.iocoder.yudao.module.system.service.ai.bo.AttendanceQueryConditionsBO;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.List;

@Service
@Validated
public class AttendanceDailyReportServiceImpl implements AttendanceDailyReportService {

    @Resource
    private AttendanceDailyReportMapper attendanceDailyReportMapper;

    @Override
    public List<AttendanceDailyReportDO> getListByConditions(AttendanceQueryConditionsBO conditions) {
        return attendanceDailyReportMapper.selectListByConditions(conditions);
    }

}
