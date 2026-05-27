package cn.iocoder.yudao.module.system.service.ai;

import cn.hutool.core.bean.BeanUtil;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AiQueryAttendanceRespVO;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AttendanceDailyReportRespVO;
import cn.iocoder.yudao.module.system.convert.ai.AttendanceAiQueryConvert;
import cn.iocoder.yudao.module.system.dal.dataobject.attendance.AttendanceDailyReportDO;
import cn.iocoder.yudao.module.system.service.ai.bo.AttendanceQueryConditionsBO;
import cn.iocoder.yudao.module.system.service.ai.parser.AttendanceNaturalLanguageParser;
import cn.iocoder.yudao.module.system.service.attendance.AttendanceDailyReportService;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
public class AiQueryServiceImpl implements AiQueryService {

    private static final String TABLE_NAME = "attendance_daily_report";

    @Resource
    private AttendanceNaturalLanguageParser attendanceNaturalLanguageParser;
    @Resource
    private AttendanceDailyReportService attendanceDailyReportService;

    @Override
    public AiQueryAttendanceRespVO queryAttendance(String message) {
        AttendanceQueryConditionsBO conditions = attendanceNaturalLanguageParser.parse(message);
        List<AttendanceDailyReportDO> list = attendanceDailyReportService.getListByConditions(conditions);
        List<AttendanceDailyReportRespVO> respList = AttendanceAiQueryConvert.INSTANCE.convertList(list);

        AiQueryAttendanceRespVO resp = new AiQueryAttendanceRespVO();
        resp.setParseSummary(attendanceNaturalLanguageParser.buildParseSummary(message, conditions, respList.size()));
        resp.setTableName(TABLE_NAME);
        resp.setConditions(buildConditionsMap(conditions));
        resp.setColumns(AttendanceAiQueryConvert.INSTANCE.buildAttendanceColumns());
        resp.setList(respList);
        resp.setTotal((long) respList.size());
        return resp;
    }

    private Map<String, Object> buildConditionsMap(AttendanceQueryConditionsBO conditions) {
        Map<String, Object> map = new HashMap<>();
        BeanUtil.beanToMap(conditions, map, false, true);
        return map;
    }

}
