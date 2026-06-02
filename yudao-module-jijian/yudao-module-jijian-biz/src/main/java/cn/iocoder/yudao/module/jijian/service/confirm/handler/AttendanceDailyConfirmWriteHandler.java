package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily.AttendanceDailyMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class AttendanceDailyConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private AttendanceDailyMapper attendanceDailyMapper;

    @Override public String getFormType() { return FormTypeConstants.ATTENDANCE; }

    @Override
    public String getBusinessTableName() {{ return "jijian_attendance_daily"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = get(row, "姓名", "员工姓名");
            if (StrUtil.isBlank(name)) throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            AttendanceDailyDO do_ = AttendanceDailyDO.builder()
                    .employeeName(name)
                    .employeeNo(get(row, "员工编号", "工号"))
                    .department(get(row, "部门"))
                    .checkinTime(parseDateTime(get(row, "上班打卡时间", "打卡时间", "上班时间")))
                    .checkinResult(get(row, "上班打卡结果", "打卡结果"))
                    .checkinLocation(get(row, "上班打卡地点", "打卡地点"))
                    .checkinRemark(get(row, "上班备注"))
                    .checkoutTime(parseDateTime(get(row, "下班打卡时间", "下班时间")))
                    .checkoutResult(get(row, "下班打卡结果"))
                    .checkoutLocation(get(row, "下班打卡地点"))
                    .checkoutRemark(get(row, "下班备注"))
                    .attendanceDate(parseDate(get(row, "考勤日期", "日期")))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            attendanceDailyMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<AttendanceDailyDO> list = attendanceDailyMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (AttendanceDailyDO d : list) {
            result.add(toSummaryMap("姓名", d.getEmployeeName(), "部门", d.getDepartment(),
                "考勤日期", d.getAttendanceDate() == null ? null : d.getAttendanceDate().toString(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private LocalDateTime parseDateTime(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return DateUtil.parseLocalDateTime(s); } catch (Exception e) { return null; }
    }

    private LocalDate parseDate(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return DateUtil.parseLocalDateTime(s).toLocalDate(); } catch (Exception e) {
            try { return LocalDate.parse(s.trim().substring(0, 10)); } catch (Exception e2) { return null; }
        }
    }
}
