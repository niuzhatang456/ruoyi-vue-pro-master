package cn.iocoder.yudao.module.system.convert.ai;

import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AiQueryColumnVO;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AttendanceDailyReportRespVO;
import cn.iocoder.yudao.module.system.dal.dataobject.attendance.AttendanceDailyReportDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

@Mapper
public interface AttendanceAiQueryConvert {

    AttendanceAiQueryConvert INSTANCE = Mappers.getMapper(AttendanceAiQueryConvert.class);

    DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    default List<AiQueryColumnVO> buildAttendanceColumns() {
        return Arrays.asList(
                new AiQueryColumnVO("employeeName", "姓名", null, 90, "left"),
                new AiQueryColumnVO("employeeNo", "员工编号", 110, null, "center"),
                new AiQueryColumnVO("deptName", "部门", null, 120, "left"),
                new AiQueryColumnVO("clockInTime", "上班打卡时间", 170, null, "center"),
                new AiQueryColumnVO("clockInResult", "上班打卡结果", 120, null, "center"),
                new AiQueryColumnVO("clockInLocation", "上班打卡地点", null, 140, "left"),
                new AiQueryColumnVO("clockInRemark", "上班备注", null, 100, "left"),
                new AiQueryColumnVO("clockOutTime", "下班打卡时间", 170, null, "center"),
                new AiQueryColumnVO("clockOutResult", "下班打卡结果", 120, null, "center"),
                new AiQueryColumnVO("clockOutLocation", "下班打卡地点", null, 140, "left"),
                new AiQueryColumnVO("clockOutRemark", "下班备注", null, 100, "left")
        );
    }

    default List<AttendanceDailyReportRespVO> convertList(List<AttendanceDailyReportDO> list) {
        return CollectionUtils.convertList(list, this::convert);
    }

    default AttendanceDailyReportRespVO convert(AttendanceDailyReportDO bean) {
        if (bean == null) {
            return null;
        }
        AttendanceDailyReportRespVO vo = new AttendanceDailyReportRespVO();
        vo.setEmployeeName(bean.getEmployeeName());
        vo.setEmployeeNo(bean.getEmployeeNo());
        vo.setDeptName(bean.getDeptName());
        vo.setClockInTime(formatDateTime(bean.getClockInTime()));
        vo.setClockInResult(bean.getClockInResult());
        vo.setClockInLocation(bean.getClockInLocation());
        vo.setClockInRemark(bean.getClockInRemark());
        vo.setClockOutTime(formatDateTime(bean.getClockOutTime()));
        vo.setClockOutResult(bean.getClockOutResult());
        vo.setClockOutLocation(bean.getClockOutLocation());
        vo.setClockOutRemark(bean.getClockOutRemark());
        return vo;
    }

    default String formatDateTime(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DATETIME_FORMATTER);
    }

}
