package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily.AttendanceDailyMapper;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.AttendanceSummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Validated
public class JijianAttendanceQueryServiceImpl implements JijianAttendanceQueryService {

    @Resource
    private AttendanceDailyMapper attendanceDailyMapper;

    @Override
    public JijianAttendancePageRespVO queryPage(JijianAttendancePageReqVO req) {
        JijianQueryTimeRangeEnum timeRange = resolveTimeRange(req.getTimeRange());
        LocalDate startDate = timeRange.startDate();

        PageResult<AttendanceDailyDO> doPage = attendanceDailyMapper.selectPageForQuery(req, startDate);

        List<JijianAttendancePageRespVO.AttendanceItemVO> voList =
                BeanUtils.toBean(doPage.getList(), JijianAttendancePageRespVO.AttendanceItemVO.class);
        PageResult<JijianAttendancePageRespVO.AttendanceItemVO> voPage =
                new PageResult<>(voList, doPage.getTotal());

        AttendanceSummaryDTO summary = buildSummary(req.getDepartment(), startDate);

        return new JijianAttendancePageRespVO(voPage, summary);
    }

    @Override
    public AttendanceSummaryDTO querySummary(JijianAiQueryIntent intent) {
        JijianQueryTimeRangeEnum timeRange = resolveTimeRange(intent.getTimeRange());
        return buildSummary(intent.getDepartment(), timeRange.startDate());
    }

    @Override
    public List<String> listDepartments() {
        return attendanceDailyMapper.selectDistinctDepartments();
    }

    // ---- private ----

    private JijianQueryTimeRangeEnum resolveTimeRange(String value) {
        JijianQueryTimeRangeEnum e = JijianQueryTimeRangeEnum.of(value);
        return e != null ? e : JijianQueryTimeRangeEnum.ONE_WEEK;
    }

    private AttendanceSummaryDTO buildSummary(String department, LocalDate startDate) {
        List<AttendanceDailyDO> all = attendanceDailyMapper.selectListForSummary(department, startDate);

        AttendanceSummaryDTO summary = new AttendanceSummaryDTO();
        summary.setTotalCount(all.size());

        Map<String, Long> byDept = all.stream()
                .filter(d -> d.getDepartment() != null && !d.getDepartment().isEmpty())
                .collect(Collectors.groupingBy(AttendanceDailyDO::getDepartment, Collectors.counting()));
        summary.setDepartmentCount(byDept.size());

        List<AttendanceSummaryDTO.DepartmentCountDTO> deptList = new ArrayList<>();
        byDept.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    AttendanceSummaryDTO.DepartmentCountDTO dto = new AttendanceSummaryDTO.DepartmentCountDTO();
                    dto.setDepartment(e.getKey());
                    dto.setCount(e.getValue());
                    deptList.add(dto);
                });
        summary.setByDepartment(deptList);

        // TODO: 考勤表暂无标准化出勤状态字段，用 checkinResult 做统计。
        //       后续增加 attendanceStatus 字段后可精确分类（出勤/调休/请假/缺勤等）。
        Map<String, Long> byStatus = all.stream()
                .collect(Collectors.groupingBy(
                        d -> d.getCheckinResult() != null ? d.getCheckinResult() : "未知",
                        Collectors.counting()));
        List<AttendanceSummaryDTO.StatusCountDTO> statusList = new ArrayList<>();
        byStatus.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> {
                    AttendanceSummaryDTO.StatusCountDTO dto = new AttendanceSummaryDTO.StatusCountDTO();
                    dto.setStatus(e.getKey());
                    dto.setCount(e.getValue());
                    statusList.add(dto);
                });
        summary.setByAttendanceStatus(statusList);

        return summary;
    }
}
