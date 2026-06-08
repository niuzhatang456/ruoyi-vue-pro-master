package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily.AttendanceDailyMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip.BusinessTripMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave.CompensatoryLeaveMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth.LeaveHealthMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal.LeavePersonalMapper;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO;
import cn.iocoder.yudao.module.jijian.service.query.JijianAttendanceAnalysisService.AnalysisResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JijianAttendanceAnalysisServiceTest {

    @InjectMocks
    private JijianAttendanceAnalysisService service;

    @Mock private AttendanceDailyMapper attendanceDailyMapper;
    @Mock private LeaveHealthMapper leaveHealthMapper;
    @Mock private LeavePersonalMapper leavePersonalMapper;
    @Mock private BusinessTripMapper businessTripMapper;
    @Mock private CompensatoryLeaveMapper compensatoryLeaveMapper;

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 3, 15);

    private AttendanceDailyDO makeAttendance(String name, String empNo, String dept, String checkin, String checkout) {
        AttendanceDailyDO r = new AttendanceDailyDO();
        r.setId(1L);
        r.setEmployeeName(name);
        r.setEmployeeNo(empNo);
        r.setDepartment(dept);
        r.setAttendanceDate(TEST_DATE);
        r.setCheckinResult(checkin);
        r.setCheckoutResult(checkout);
        return r;
    }

    @BeforeEach
    void setUp() {
        when(leaveHealthMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(leavePersonalMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(businessTripMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
        when(compensatoryLeaveMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());
    }

    @Test
    void test_normal_attendance_not_anomaly() {
        AttendanceDailyDO r = makeAttendance("张三", "E001", "办公室", "正常", "正常");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getRawAnomalies()).isEmpty();
        assertThat(result.getUnexplainedAnomalies()).isEmpty();
        assertThat(result.getMetrics()).isNotEmpty();
    }

    @Test
    void test_missing_checkin_with_no_explanation_is_suspected_absence() {
        AttendanceDailyDO r = makeAttendance("李四", "E002", "第一纪检监察室", "缺卡", "正常");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getRawAnomalies()).hasSize(1);
        assertThat(result.getUnexplainedAnomalies()).hasSize(1);
        assertThat(result.getExplainedAnomalies()).isEmpty();
    }

    @Test
    void test_missing_checkin_covered_by_personal_leave_is_explained() {
        AttendanceDailyDO r = makeAttendance("王五", "E003", "办公室", "缺卡", "缺卡");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        LeavePersonalDO leave = new LeavePersonalDO();
        leave.setApplicantName("王五");
        leave.setEmployeeNo("E003");
        leave.setDepartment("办公室");
        leave.setStartTime(TEST_DATE.atStartOfDay());
        leave.setEndTime(TEST_DATE.plusDays(1).atStartOfDay());
        when(leavePersonalMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(leave));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getUnexplainedAnomalies()).isEmpty();
        assertThat(result.getExplainedAnomalies()).hasSize(1);
        assertThat(result.getExplainedAnomalies().get(0).getExplainSource()).isEqualTo("事假");
    }

    @Test
    void test_missing_checkin_covered_by_business_trip_is_explained() {
        AttendanceDailyDO r = makeAttendance("赵六", "E004", "第二纪检监察室", "未打卡", "正常");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        BusinessTripDO trip = new BusinessTripDO();
        trip.setApplicantName("赵六");
        trip.setEmployeeNo("E004");
        trip.setDepartment("第二纪检监察室");
        trip.setStartTime(TEST_DATE.atStartOfDay());
        trip.setEndTime(TEST_DATE.plusDays(2).atStartOfDay());
        when(businessTripMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(trip));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getUnexplainedAnomalies()).isEmpty();
        assertThat(result.getExplainedAnomalies()).hasSize(1);
        assertThat(result.getExplainedAnomalies().get(0).getExplainSource()).isEqualTo("出差");
    }

    @Test
    void test_missing_checkin_covered_by_compensatory_leave_is_explained() {
        AttendanceDailyDO r = makeAttendance("孙七", "E005", "办公室", "正常", "早退");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        CompensatoryLeaveDO comp = new CompensatoryLeaveDO();
        comp.setApplicantName("孙七");
        comp.setEmployeeNo("E005");
        comp.setDepartment("办公室");
        comp.setCompensatoryStartTime(TEST_DATE.atStartOfDay());
        comp.setCompensatoryEndTime(TEST_DATE.plusDays(1).atStartOfDay());
        when(compensatoryLeaveMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(comp));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getUnexplainedAnomalies()).isEmpty();
        assertThat(result.getExplainedAnomalies()).hasSize(1);
        assertThat(result.getExplainedAnomalies().get(0).getExplainSource()).isEqualTo("调休");
    }

    @Test
    void test_missing_checkin_covered_by_recuperation_leave_is_explained() {
        AttendanceDailyDO r = makeAttendance("周八", "E006", "第一纪检监察室", "异常", "缺卡");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(r));

        LeaveHealthDO health = new LeaveHealthDO();
        health.setApplicantName("周八");
        health.setEmployeeNo("E006");
        health.setDepartment("第一纪检监察室");
        health.setStartTime(TEST_DATE.minusDays(1).atStartOfDay());
        health.setEndTime(TEST_DATE.plusDays(3).atStartOfDay());
        when(leaveHealthMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.singletonList(health));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getUnexplainedAnomalies()).isEmpty();
        assertThat(result.getExplainedAnomalies()).hasSize(1);
        assertThat(result.getExplainedAnomalies().get(0).getExplainSource()).isEqualTo("疗休养");
    }

    @Test
    void test_metrics_not_empty() {
        AttendanceDailyDO r1 = makeAttendance("甲", "E010", "A部", "正常", "正常");
        AttendanceDailyDO r2 = makeAttendance("乙", "E011", "B部", "缺卡", "缺卡");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Arrays.asList(r1, r2));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getMetrics()).isNotEmpty();
        boolean hasTotal = result.getMetrics().stream()
                .anyMatch(m -> "totalAttendanceRecords".equals(m.getKey()));
        assertThat(hasTotal).isTrue();
    }

    @Test
    void test_charts_not_empty_when_data_exists() {
        AttendanceDailyDO r1 = makeAttendance("甲", "E010", "A部", "正常", "正常");
        AttendanceDailyDO r2 = makeAttendance("乙", "E011", "B部", "缺卡", "缺卡");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Arrays.asList(r1, r2));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getCharts()).isNotEmpty();
        boolean hasPie = result.getCharts().stream().anyMatch(c -> "pie".equals(c.getType()));
        assertThat(hasPie).isTrue();
    }

    @Test
    void test_pie_chart_data_sums_correctly() {
        AttendanceDailyDO normal = makeAttendance("甲", "E010", "A部", "正常", "正常");
        AttendanceDailyDO missing = makeAttendance("乙", "E011", "B部", "缺卡", "缺卡");
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Arrays.asList(normal, missing));

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        JijianChartVO pieChart = result.getCharts().stream()
                .filter(c -> "pie".equals(c.getType()) && c.getTitle().contains("考勤状态"))
                .findFirst().orElse(null);
        assertThat(pieChart).isNotNull();
        int sum = pieChart.getData().stream()
                .mapToInt(d -> ((Number) d.get("value")).intValue()).sum();
        assertThat(sum).isEqualTo(2);
    }

    @Test
    void test_database_context_meta_contains_all_5_tables() {
        when(attendanceDailyMapper.selectListInDateRange(any(), any(), anyString()))
                .thenReturn(Collections.emptyList());

        AnalysisResult result = service.analyze("ALL", "ONE_MONTH");

        assertThat(result.getDatabaseContextMeta()).isNotNull();
        assertThat(result.getDatabaseContextMeta().getTablesUsed())
                .contains("ATTENDANCE_DAILY", "RECUPERATION_LEAVE", "PERSONAL_LEAVE",
                        "BUSINESS_TRIP", "COMPENSATORY_LEAVE");
        assertThat(result.getDatabaseContextMeta().getDataSource()).isEqualTo("database");
        assertThat(result.getDatabaseContextMeta().isSensitiveFieldsRemoved()).isTrue();
    }
}
