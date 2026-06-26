package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAnalysisTableVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO.JijianChartSeriesVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianDatabaseContextMetaVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianMetricVO;
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
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryTimeRangeEnum;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class JijianAttendanceAnalysisService {

    private static final Set<String> ANOMALY_KEYWORDS = new HashSet<>(Arrays.asList(
            "缺卡", "未打卡", "异常", "迟到", "早退"
    ));

    @Resource
    private AttendanceDailyMapper attendanceDailyMapper;
    @Resource
    private LeaveHealthMapper leaveHealthMapper;
    @Resource
    private LeavePersonalMapper leavePersonalMapper;
    @Resource
    private BusinessTripMapper businessTripMapper;
    @Resource
    private CompensatoryLeaveMapper compensatoryLeaveMapper;

    public AnalysisResult analyze(String department, String timeRange) {
        JijianQueryTimeRangeEnum rangeEnum = JijianQueryTimeRangeEnum.of(timeRange);
        if (rangeEnum == null) {
            rangeEnum = JijianQueryTimeRangeEnum.ONE_MONTH;
        }
        LocalDate startDate = rangeEnum.startDate();
        LocalDate endDate = LocalDate.now();
        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

        List<AttendanceDailyDO> attendanceList = attendanceDailyMapper.selectListInDateRange(startDate, endDate, department);
        List<LeaveHealthDO> leaveHealthList = leaveHealthMapper.selectListInDateRange(startDt, endDt, department);
        List<LeavePersonalDO> leavePersonalList = leavePersonalMapper.selectListInDateRange(startDt, endDt, department);
        List<BusinessTripDO> businessTripList = businessTripMapper.selectListInDateRange(startDt, endDt, department);
        List<CompensatoryLeaveDO> compensatoryList = compensatoryLeaveMapper.selectListInDateRange(startDt, endDt, department);

        List<AnomalyItem> rawAnomalies = findRawAnomalies(attendanceList);
        List<AnomalyItem> explained = new ArrayList<>();
        List<AnomalyItem> unexplained = new ArrayList<>();

        for (AnomalyItem item : rawAnomalies) {
            ExplainSource source = findExplanation(item, leaveHealthList, leavePersonalList, businessTripList, compensatoryList);
            if (source != null) {
                item.setExplainSource(source.label);
                explained.add(item);
            } else {
                unexplained.add(item);
            }
        }

        AnalysisResult result = new AnalysisResult();
        result.setAttendanceList(attendanceList);
        result.setLeaveHealthList(leaveHealthList);
        result.setLeavePersonalList(leavePersonalList);
        result.setBusinessTripList(businessTripList);
        result.setCompensatoryList(compensatoryList);
        result.setRawAnomalies(rawAnomalies);
        result.setExplainedAnomalies(explained);
        result.setUnexplainedAnomalies(unexplained);
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        result.setTimeRangeLabel(rangeEnum.getLabel());

        result.setMetrics(buildMetrics(attendanceList, rawAnomalies, explained, unexplained));
        result.setCharts(buildCharts(attendanceList, rawAnomalies, explained, unexplained, leaveHealthList, leavePersonalList, businessTripList, compensatoryList));
        result.setTables(buildTables(unexplained, explained));
        result.setDatabaseContextMeta(buildMeta(attendanceList, leaveHealthList, leavePersonalList, businessTripList, compensatoryList, rangeEnum.getLabel()));

        return result;
    }

    private List<AnomalyItem> findRawAnomalies(List<AttendanceDailyDO> list) {
        List<AnomalyItem> result = new ArrayList<>();
        for (AttendanceDailyDO r : list) {
            boolean checkinAnomal = isAnomaly(r.getCheckinResult());
            boolean checkoutAnomal = isAnomaly(r.getCheckoutResult());
            if (checkinAnomal || checkoutAnomal) {
                AnomalyItem item = new AnomalyItem();
                item.setAttendanceId(r.getId());
                item.setEmployeeName(r.getEmployeeName());
                item.setEmployeeNo(r.getEmployeeNo());
                item.setDepartment(r.getDepartment());
                item.setAttendanceDate(r.getAttendanceDate());
                item.setCheckinResult(r.getCheckinResult());
                item.setCheckoutResult(r.getCheckoutResult());
                item.setAnomalyType((checkinAnomal ? "上班" + nvl(r.getCheckinResult()) : "") +
                        (checkoutAnomal ? (checkinAnomal ? "+" : "") + "下班" + nvl(r.getCheckoutResult()) : ""));
                result.add(item);
            }
        }
        return result;
    }

    private ExplainSource findExplanation(AnomalyItem item,
                                          List<LeaveHealthDO> healthList,
                                          List<LeavePersonalDO> personalList,
                                          List<BusinessTripDO> tripList,
                                          List<CompensatoryLeaveDO> compensatoryList) {
        LocalDate date = item.getAttendanceDate();
        if (date == null) return null;
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.atTime(LocalTime.MAX);

        for (LeaveHealthDO r : healthList) {
            if (matchEmployee(item, r.getApplicantName(), r.getEmployeeNo(), r.getDepartment())
                    && overlaps(r.getStartTime(), r.getEndTime(), dayStart, dayEnd)) {
                return new ExplainSource("疗休养");
            }
        }
        for (LeavePersonalDO r : personalList) {
            if (matchEmployee(item, r.getApplicantName(), r.getEmployeeNo(), r.getDepartment())
                    && overlaps(r.getStartTime(), r.getEndTime(), dayStart, dayEnd)) {
                return new ExplainSource("事假");
            }
        }
        for (BusinessTripDO r : tripList) {
            if (matchEmployee(item, r.getApplicantName(), r.getEmployeeNo(), r.getDepartment())
                    && overlaps(businessTripStart(r), businessTripEnd(r), dayStart, dayEnd)) {
                return new ExplainSource("出差");
            }
        }
        for (CompensatoryLeaveDO r : compensatoryList) {
            if (matchEmployee(item, r.getApplicantName(), r.getEmployeeNo(), r.getDepartment())
                    && overlaps(r.getCompensatoryStartTime(), r.getCompensatoryEndTime(), dayStart, dayEnd)) {
                return new ExplainSource("调休");
            }
        }
        return null;
    }

    private boolean matchEmployee(AnomalyItem item, String name, String empNo, String dept) {
        if (item.getEmployeeNo() != null && !item.getEmployeeNo().isBlank()
                && empNo != null && !empNo.isBlank()) {
            return item.getEmployeeNo().equals(empNo);
        }
        return item.getEmployeeName() != null && item.getEmployeeName().equals(name)
                && (dept == null || dept.equals(item.getDepartment()));
    }

    private boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        if (s1 == null || e1 == null) return false;
        return s1.isBefore(e2) && e1.isAfter(s2);
    }

    private boolean isAnomaly(String result) {
        if (result == null || result.isBlank()) return false;
        for (String kw : ANOMALY_KEYWORDS) {
            if (result.contains(kw)) return true;
        }
        return false;
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }

    // ========================= 指标构建 =========================

    private List<JijianMetricVO> buildMetrics(List<AttendanceDailyDO> attendanceList,
                                               List<AnomalyItem> rawAnomalies,
                                               List<AnomalyItem> explained,
                                               List<AnomalyItem> unexplained) {
        int total = attendanceList.size();
        long people = attendanceList.stream()
                .map(r -> r.getEmployeeNo() != null ? r.getEmployeeNo() : r.getEmployeeName())
                .filter(Objects::nonNull)
                .distinct().count();
        long depts = attendanceList.stream()
                .map(AttendanceDailyDO::getDepartment).filter(Objects::nonNull).distinct().count();
        long unexplainedPeople = unexplained.stream()
                .map(r -> r.getEmployeeNo() != null ? r.getEmployeeNo() : r.getEmployeeName())
                .filter(Objects::nonNull).distinct().count();
        int normalCount = total - rawAnomalies.size();

        List<JijianMetricVO> metrics = new ArrayList<>();
        metrics.add(JijianMetricVO.builder().key("totalAttendanceRecords").label("考勤记录总数").value(total).unit("条").build());
        metrics.add(JijianMetricVO.builder().key("totalPeople").label("涉及人数").value(people).unit("人").build());
        metrics.add(JijianMetricVO.builder().key("totalDepartments").label("涉及部门数").value(depts).unit("个").build());
        metrics.add(JijianMetricVO.builder().key("normalCount").label("正常出勤").value(normalCount).unit("次").build());
        metrics.add(JijianMetricVO.builder().key("rawAnomalyCount").label("原始缺卡/异常次数").value(rawAnomalies.size()).unit("次").build());
        metrics.add(JijianMetricVO.builder().key("explainedCount").label("已解释缺卡次数").value(explained.size()).unit("次").build());
        metrics.add(JijianMetricVO.builder().key("suspectedAbsenceCount").label("疑似缺勤次数").value(unexplained.size()).unit("次").build());
        metrics.add(JijianMetricVO.builder().key("suspectedAbsencePeople").label("疑似缺勤人数").value(unexplainedPeople).unit("人").build());
        return metrics;
    }

    // ========================= 图表构建 =========================

    private List<JijianChartVO> buildCharts(List<AttendanceDailyDO> attendanceList,
                                             List<AnomalyItem> rawAnomalies,
                                             List<AnomalyItem> explained,
                                             List<AnomalyItem> unexplained,
                                             List<LeaveHealthDO> healthList,
                                             List<LeavePersonalDO> personalList,
                                             List<BusinessTripDO> tripList,
                                             List<CompensatoryLeaveDO> compensatoryList) {
        List<JijianChartVO> charts = new ArrayList<>();
        int total = attendanceList.size();
        int normalCount = total - rawAnomalies.size();

        // 1. 考勤状态占比饼图
        List<Map<String, Object>> pieData = new ArrayList<>();
        pieData.add(mapOf("name", "正常出勤", "value", normalCount));
        pieData.add(mapOf("name", "已解释缺卡", "value", explained.size()));
        pieData.add(mapOf("name", "疑似缺勤", "value", unexplained.size()));
        charts.add(JijianChartVO.builder()
                .type("pie").title("考勤状态占比")
                .description("正常出勤、已解释缺卡、疑似缺勤占比")
                .data(pieData).build());

        // 2. 各部门出勤率柱状图
        Map<String, long[]> deptStats = new LinkedHashMap<>();
        for (AttendanceDailyDO r : attendanceList) {
            String dept = nvl(r.getDepartment());
            deptStats.computeIfAbsent(dept, k -> new long[]{0, 0})[0]++;
        }
        for (AnomalyItem item : unexplained) {
            String dept = nvl(item.getDepartment());
            long[] v = deptStats.computeIfAbsent(dept, k -> new long[]{0, 0});
            v[1]++;
        }
        if (!deptStats.isEmpty()) {
            List<String> deptNames = new ArrayList<>(deptStats.keySet());
            List<Object> attendanceRates = new ArrayList<>();
            List<Object> absenceCounts = new ArrayList<>();
            for (String dept : deptNames) {
                long[] v = deptStats.get(dept);
                double rate = v[0] == 0 ? 100.0 : Math.round((v[0] - v[1]) * 1000.0 / v[0]) / 10.0;
                attendanceRates.add(rate);
                absenceCounts.add(v[1]);
            }
            charts.add(JijianChartVO.builder()
                    .type("bar").title("各部门出勤率")
                    .xAxis(deptNames)
                    .series(Collections.singletonList(
                            JijianChartSeriesVO.builder().name("出勤率(%)").data(attendanceRates).build()))
                    .build());
            charts.add(JijianChartVO.builder()
                    .type("bar").title("各部门疑似缺勤次数")
                    .xAxis(deptNames)
                    .series(Collections.singletonList(
                            JijianChartSeriesVO.builder().name("疑似缺勤次数").data(absenceCounts).build()))
                    .build());
        }

        // 3. 月度趋势折线图
        Map<String, long[]> monthStats = new TreeMap<>();
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (AttendanceDailyDO r : attendanceList) {
            if (r.getAttendanceDate() == null) continue;
            String month = r.getAttendanceDate().format(monthFmt);
            monthStats.computeIfAbsent(month, k -> new long[]{0, 0})[0]++;
        }
        for (AnomalyItem item : unexplained) {
            if (item.getAttendanceDate() == null) continue;
            String month = item.getAttendanceDate().format(monthFmt);
            long[] v = monthStats.computeIfAbsent(month, k -> new long[]{0, 0});
            v[1]++;
        }
        if (monthStats.size() > 1) {
            List<String> months = new ArrayList<>(monthStats.keySet());
            List<Object> rates = new ArrayList<>();
            List<Object> absences = new ArrayList<>();
            for (String m : months) {
                long[] v = monthStats.get(m);
                double rate = v[0] == 0 ? 100.0 : Math.round((v[0] - v[1]) * 1000.0 / v[0]) / 10.0;
                rates.add(rate);
                absences.add(v[1]);
            }
            List<JijianChartSeriesVO> lineSeries = new ArrayList<>();
            lineSeries.add(JijianChartSeriesVO.builder().name("出勤率(%)").data(rates).build());
            lineSeries.add(JijianChartSeriesVO.builder().name("疑似缺勤次数").data(absences).build());
            charts.add(JijianChartVO.builder()
                    .type("line").title("月度出勤趋势")
                    .xAxis(months).series(lineSeries).build());
        }

        // 4. 解释来源占比饼图
        Map<String, Long> sourceCounts = explained.stream()
                .collect(Collectors.groupingBy(AnomalyItem::getExplainSource, Collectors.counting()));
        long noExplain = unexplained.size();
        List<Map<String, Object>> sourcePie = new ArrayList<>();
        sourceCounts.forEach((k, v) -> sourcePie.add(mapOf("name", k, "value", v)));
        if (noExplain > 0) sourcePie.add(mapOf("name", "无解释", "value", noExplain));
        if (!sourcePie.isEmpty()) {
            charts.add(JijianChartVO.builder()
                    .type("pie").title("缺卡原因构成")
                    .description("疗休养、事假、出差、调休及无解释缺卡占比")
                    .data(sourcePie).build());
        }

        return charts;
    }

    // ========================= 表格构建 =========================

    private List<JijianAnalysisTableVO> buildTables(List<AnomalyItem> unexplained, List<AnomalyItem> explained) {
        List<JijianAnalysisTableVO> tables = new ArrayList<>();

        List<Map<String, String>> absenceColumns = Arrays.asList(
                col("employeeName", "姓名"), col("department", "部门"),
                col("attendanceDate", "考勤日期"), col("anomalyType", "异常类型"),
                col("checkinResult", "上班打卡"), col("checkoutResult", "下班打卡")
        );
        List<Map<String, Object>> absenceRows = unexplained.stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employeeName", nvl(item.getEmployeeName()));
                    row.put("department", nvl(item.getDepartment()));
                    row.put("attendanceDate", item.getAttendanceDate() != null ? item.getAttendanceDate().toString() : "");
                    row.put("anomalyType", nvl(item.getAnomalyType()));
                    row.put("checkinResult", nvl(item.getCheckinResult()));
                    row.put("checkoutResult", nvl(item.getCheckoutResult()));
                    return row;
                }).collect(Collectors.toList());
        tables.add(JijianAnalysisTableVO.builder()
                .title("疑似缺勤明细（" + unexplained.size() + " 条）")
                .columns(absenceColumns).rows(absenceRows).build());

        List<Map<String, String>> explainColumns = Arrays.asList(
                col("employeeName", "姓名"), col("department", "部门"),
                col("attendanceDate", "考勤日期"), col("anomalyType", "异常类型"),
                col("explainSource", "解释来源")
        );
        List<Map<String, Object>> explainRows = explained.stream()
                .map(item -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("employeeName", nvl(item.getEmployeeName()));
                    row.put("department", nvl(item.getDepartment()));
                    row.put("attendanceDate", item.getAttendanceDate() != null ? item.getAttendanceDate().toString() : "");
                    row.put("anomalyType", nvl(item.getAnomalyType()));
                    row.put("explainSource", nvl(item.getExplainSource()));
                    return row;
                }).collect(Collectors.toList());
        tables.add(JijianAnalysisTableVO.builder()
                .title("已解释缺卡明细（" + explained.size() + " 条）")
                .columns(explainColumns).rows(explainRows).build());

        return tables;
    }

    private JijianDatabaseContextMetaVO buildMeta(List<AttendanceDailyDO> a, List<LeaveHealthDO> h,
                                                   List<LeavePersonalDO> p, List<BusinessTripDO> t,
                                                   List<CompensatoryLeaveDO> c, String timeRangeLabel) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("ATTENDANCE_DAILY", a.size());
        counts.put("RECUPERATION_LEAVE", h.size());
        counts.put("PERSONAL_LEAVE", p.size());
        counts.put("BUSINESS_TRIP", t.size());
        counts.put("COMPENSATORY_LEAVE", c.size());
        return JijianDatabaseContextMetaVO.builder()
                .tablesUsed(new ArrayList<>(counts.keySet()))
                .rowCounts(counts)
                .dataSource("database")
                .sensitiveFieldsRemoved(true)
                .truncated(false)
                .timeRange(timeRangeLabel)
                .build();
    }

    private Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private LocalDateTime businessTripStart(BusinessTripDO item) {
        return item.getStartDate() != null ? item.getStartDate() : item.getStartTime();
    }

    private LocalDateTime businessTripEnd(BusinessTripDO item) {
        return item.getEndDate() != null ? item.getEndDate() : item.getEndTime();
    }

    private Map<String, String> col(String key, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        return m;
    }

    // ========================= 内部数据结构 =========================

    @Data
    public static class AnomalyItem {
        private Long attendanceId;
        private String employeeName;
        private String employeeNo;
        private String department;
        private LocalDate attendanceDate;
        private String checkinResult;
        private String checkoutResult;
        private String anomalyType;
        private String explainSource;
    }

    private static class ExplainSource {
        final String label;
        ExplainSource(String label) { this.label = label; }
    }

    @Data
    public static class AnalysisResult {
        private List<AttendanceDailyDO> attendanceList;
        private List<LeaveHealthDO> leaveHealthList;
        private List<LeavePersonalDO> leavePersonalList;
        private List<BusinessTripDO> businessTripList;
        private List<CompensatoryLeaveDO> compensatoryList;
        private List<AnomalyItem> rawAnomalies;
        private List<AnomalyItem> explainedAnomalies;
        private List<AnomalyItem> unexplainedAnomalies;
        private LocalDate startDate;
        private LocalDate endDate;
        private String timeRangeLabel;
        private List<JijianMetricVO> metrics;
        private List<JijianChartVO> charts;
        private List<JijianAnalysisTableVO> tables;
        private JijianDatabaseContextMetaVO databaseContextMeta;
    }
}
