package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAnalysisTableVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianChartVO.JijianChartSeriesVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianDatabaseContextMetaVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianMetricVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip.BusinessTripMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave.CompensatoryLeaveMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth.LeaveHealthMapper;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal.LeavePersonalMapper;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
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

/**
 * 非敏感四表（调休/事假/出差/疗休养）直接明细查询服务。
 * 提供完整 records 给 DeepSeek 进行分析，不做脱敏（无敏感字段）。
 */
@Slf4j
@Service
public class JijianDirectTableAnalysisService {

    private static final int MAX_RECORDS_TO_DEEPSEEK = 200;

    @Resource
    private CompensatoryLeaveMapper compensatoryLeaveMapper;
    @Resource
    private LeaveHealthMapper leaveHealthMapper;
    @Resource
    private LeavePersonalMapper leavePersonalMapper;
    @Resource
    private BusinessTripMapper businessTripMapper;

    public DirectAnalysisResult analyze(String formType, String department, String personName, String timeRange) {
        JijianQueryTimeRangeEnum rangeEnum = JijianQueryTimeRangeEnum.of(timeRange);
        if (rangeEnum == null) {
            rangeEnum = JijianQueryTimeRangeEnum.ONE_YEAR;
        }
        LocalDate startDate = rangeEnum.startDate();
        LocalDate endDate = LocalDate.now();
        LocalDateTime startDt = startDate.atStartOfDay();
        LocalDateTime endDt = endDate.atTime(LocalTime.MAX);

        String formTypeNorm = JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue();
        if (JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue().equals(formType)) {
            formTypeNorm = JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue();
        } else if (JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue().equals(formType)) {
            formTypeNorm = JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue();
        } else if (JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue().equals(formType)) {
            formTypeNorm = JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue();
        }

        List<Map<String, Object>> allRecords = loadRecords(formTypeNorm, department, startDt, endDt);
        List<Map<String, Object>> filtered = filterByPerson(allRecords, personName);

        DirectAnalysisResult result = new DirectAnalysisResult();
        result.setFormType(formTypeNorm);
        result.setDepartment(department);
        result.setPersonName(personName);
        result.setTimeRangeLabel(rangeEnum.getLabel());
        result.setStartDate(startDate);
        result.setEndDate(endDate);
        result.setTotalCount(allRecords.size());
        result.setFilteredRecords(filtered);
        result.setMetrics(buildMetrics(formTypeNorm, filtered, allRecords, personName));
        result.setCharts(buildCharts(formTypeNorm, filtered, allRecords, personName));
        result.setTables(buildTables(formTypeNorm, filtered, personName));
        result.setDatabaseContextMeta(buildMeta(formTypeNorm, allRecords.size(), filtered.size(), rangeEnum.getLabel()));
        result.setSchema(buildSchema(formTypeNorm));
        return result;
    }

    private List<Map<String, Object>> loadRecords(String formType, String department,
                                                   LocalDateTime startDt, LocalDateTime endDt) {
        if (JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)) {
            List<CompensatoryLeaveDO> list = compensatoryLeaveMapper.selectListInDateRange(startDt, endDt, department);
            return list.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("applicantName", nvl(r.getApplicantName()));
                m.put("employeeNo", nvl(r.getEmployeeNo()));
                m.put("department", nvl(r.getDepartment()));
                m.put("overtimeStartTime", fmtDt(r.getOvertimeStartTime()));
                m.put("overtimeStartShift", nvl(r.getOvertimeStartShift()));
                m.put("overtimeEndTime", fmtDt(r.getOvertimeEndTime()));
                m.put("overtimeEndShift", nvl(r.getOvertimeEndShift()));
                m.put("compensatoryStartTime", fmtDt(r.getCompensatoryStartTime()));
                m.put("compensatoryStartShift", nvl(r.getCompensatoryStartShift()));
                m.put("compensatoryEndTime", fmtDt(r.getCompensatoryEndTime()));
                m.put("compensatoryEndShift", nvl(r.getCompensatoryEndShift()));
                m.put("compensatoryDuration", nvl(r.getCompensatoryDuration()));
                m.put("isOutside", r.getIsOutside() != null ? r.getIsOutside() : "");
                m.put("outsideLocation", nvl(r.getOutsideLocation()));
                m.put("remark", nvl(r.getRemark()));
                return m;
            }).collect(Collectors.toList());
        }
        if (JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue().equals(formType)) {
            List<LeaveHealthDO> list = leaveHealthMapper.selectListInDateRange(startDt, endDt, department);
            return list.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("applicantName", nvl(r.getApplicantName()));
                m.put("employeeNo", nvl(r.getEmployeeNo()));
                m.put("department", nvl(r.getDepartment()));
                m.put("leaveLocation", nvl(r.getLeaveLocation()));
                m.put("startTime", fmtDt(r.getStartTime()));
                m.put("endTime", fmtDt(r.getEndTime()));
                m.put("leaveDays", r.getLeaveDays() != null ? r.getLeaveDays().toPlainString() : "");
                m.put("workYears", nvl(r.getWorkYears()));
                m.put("startWorkTime", fmtDt(r.getStartWorkTime()));
                m.put("remark", nvl(r.getRemark()));
                return m;
            }).collect(Collectors.toList());
        }
        if (JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue().equals(formType)) {
            List<LeavePersonalDO> list = leavePersonalMapper.selectListInDateRange(startDt, endDt, department);
            return list.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("applicantName", nvl(r.getApplicantName()));
                m.put("employeeNo", nvl(r.getEmployeeNo()));
                m.put("department", nvl(r.getDepartment()));
                m.put("leaveType", nvl(r.getLeaveType()));
                m.put("leaveReason", nvl(r.getLeaveReason()));
                m.put("startTime", fmtDt(r.getStartTime()));
                m.put("endTime", fmtDt(r.getEndTime()));
                m.put("leaveDays", r.getLeaveDays() != null ? r.getLeaveDays().toPlainString() : "");
                m.put("leaveStatus", nvl(r.getLeaveStatus()));
                m.put("isOutside", r.getIsOutside() != null ? r.getIsOutside() : "");
                m.put("outsideLocation", nvl(r.getOutsideLocation()));
                m.put("remark", nvl(r.getRemark()));
                return m;
            }).collect(Collectors.toList());
        }
        if (JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue().equals(formType)) {
            List<BusinessTripDO> list = businessTripMapper.selectListInDateRange(startDt, endDt, department);
            return list.stream().map(r -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("applicantName", nvl(r.getApplicantName()));
                m.put("employeeNo", nvl(r.getEmployeeNo()));
                m.put("department", nvl(r.getDepartment()));
                m.put("tripReason", nvl(r.getTripReason()));
                m.put("departurePlace", nvl(r.getDeparturePlace()));
                m.put("destination", nvl(r.getDestination()));
                m.put("startTime", fmtDt(businessTripStart(r)));
                m.put("endTime", fmtDt(businessTripEnd(r)));
                m.put("leaveDays", r.getTripDays() != null ? r.getTripDays().toPlainString() : "");
                m.put("tripPersonnel", nvl(r.getTripPersonnel()));
                m.put("tripPeopleCount", r.getTripPeopleCount() != null ? r.getTripPeopleCount() : "");
                m.put("isOutside", nvl(r.getIsOutside()));
                m.put("outsideLocation", nvl(r.getOutsideLocation()));
                m.put("remark", nvl(r.getRemark()));
                return m;
            }).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private List<Map<String, Object>> filterByPerson(List<Map<String, Object>> records, String personName) {
        if (personName == null || personName.isBlank()) {
            return records;
        }
        return records.stream()
                .filter(r -> personName.equals(r.get("applicantName")))
                .collect(Collectors.toList());
    }

    private List<JijianMetricVO> buildMetrics(String formType, List<Map<String, Object>> filtered,
                                               List<Map<String, Object>> all, String personName) {
        List<JijianMetricVO> metrics = new ArrayList<>();
        String typeName = typeName(formType);
        boolean isPersonQuery = personName != null && !personName.isBlank();

        if (isPersonQuery) {
            metrics.add(JijianMetricVO.builder().key("personName").label("查询人员").value(personName).build());
            metrics.add(JijianMetricVO.builder().key("recordCount").label(typeName + "记录数").value(filtered.size()).unit("条").build());
            if (JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)) {
                double totalDuration = filtered.stream()
                        .mapToDouble(r -> parseDuration(r.get("compensatoryDuration")))
                        .sum();
                metrics.add(JijianMetricVO.builder().key("totalDuration").label("调休总时长").value(totalDuration).unit("天").build());
            } else {
                double totalDays = filtered.stream()
                        .mapToDouble(r -> parseDuration(r.get("leaveDays")))
                        .sum();
                metrics.add(JijianMetricVO.builder().key("totalDays").label(typeName + "总天数").value(totalDays).unit("天").build());
            }
        } else {
            long peopleCount = all.stream().map(r -> (String) r.get("applicantName"))
                    .filter(s -> s != null && !s.isBlank()).distinct().count();
            long deptCount = all.stream().map(r -> (String) r.get("department"))
                    .filter(s -> s != null && !s.isBlank()).distinct().count();
            metrics.add(JijianMetricVO.builder().key("totalRecords").label(typeName + "总记录数").value(all.size()).unit("条").build());
            metrics.add(JijianMetricVO.builder().key("totalPeople").label("涉及人数").value(peopleCount).unit("人").build());
            metrics.add(JijianMetricVO.builder().key("totalDepts").label("涉及部门数").value(deptCount).unit("个").build());
        }
        return metrics;
    }

    private List<JijianChartVO> buildCharts(String formType, List<Map<String, Object>> filtered,
                                             List<Map<String, Object>> all, String personName) {
        List<JijianChartVO> charts = new ArrayList<>();
        boolean isPersonQuery = personName != null && !personName.isBlank();
        List<Map<String, Object>> source = isPersonQuery ? filtered : all;
        if (source.isEmpty()) return charts;

        String typeName = typeName(formType);
        String durationKey = JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)
                ? "compensatoryDuration" : "leaveDays";

        // 各部门记录数柱状图
        Map<String, Long> deptCounts = source.stream()
                .collect(Collectors.groupingBy(r -> nvlStr(r.get("department")), Collectors.counting()));
        if (!deptCounts.isEmpty() && !isPersonQuery) {
            List<String> depts = new ArrayList<>(deptCounts.keySet());
            List<Object> counts = depts.stream().map(d -> (Object) deptCounts.get(d)).collect(Collectors.toList());
            charts.add(JijianChartVO.builder()
                    .type("bar").title("各部门" + typeName + "次数")
                    .xAxis(depts)
                    .series(Collections.singletonList(JijianChartSeriesVO.builder().name("次数").data(counts).build()))
                    .build());

            // 占比饼图
            List<Map<String, Object>> pieData = new ArrayList<>();
            deptCounts.forEach((k, v) -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", k);
                item.put("value", v);
                pieData.add(item);
            });
            charts.add(JijianChartVO.builder()
                    .type("pie").title("各部门" + typeName + "次数占比")
                    .data(pieData).build());
        }

        // 按人员聚合（部门查询时）
        if (!isPersonQuery) {
            Map<String, Double> personDays = new LinkedHashMap<>();
            for (Map<String, Object> r : source) {
                String name = nvlStr(r.get("applicantName"));
                if (!name.isBlank()) {
                    personDays.merge(name, parseDuration(r.get(durationKey)), Double::sum);
                }
            }
            if (!personDays.isEmpty()) {
                List<Map.Entry<String, Double>> sorted = personDays.entrySet().stream()
                        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                        .limit(20)
                        .collect(Collectors.toList());
                List<String> names = sorted.stream().map(Map.Entry::getKey).collect(Collectors.toList());
                List<Object> days = sorted.stream().map(e -> (Object) e.getValue()).collect(Collectors.toList());
                charts.add(JijianChartVO.builder()
                        .type("bar").title("人员" + typeName + "时长排名（前20）")
                        .xAxis(names)
                        .series(Collections.singletonList(JijianChartSeriesVO.builder().name("天数").data(days).build()))
                        .build());
            }
        }

        // 月度趋势（仅记录数超过1个月时才有意义）
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        String timeKey = JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)
                ? "compensatoryStartTime" : "startTime";
        Map<String, Long> monthCounts = new TreeMap<>();
        for (Map<String, Object> r : source) {
            String dtStr = nvlStr(r.get(timeKey));
            if (!dtStr.isBlank() && dtStr.length() >= 7) {
                String month = dtStr.substring(0, 7);
                monthCounts.merge(month, 1L, Long::sum);
            }
        }
        if (monthCounts.size() > 1) {
            List<String> months = new ArrayList<>(monthCounts.keySet());
            List<Object> cnts = months.stream().map(m -> (Object) monthCounts.get(m)).collect(Collectors.toList());
            charts.add(JijianChartVO.builder()
                    .type("line").title(typeName + "月度趋势")
                    .xAxis(months)
                    .series(Collections.singletonList(JijianChartSeriesVO.builder().name("次数").data(cnts).build()))
                    .build());
        }

        return charts;
    }

    private List<JijianAnalysisTableVO> buildTables(String formType, List<Map<String, Object>> filtered, String personName) {
        List<JijianAnalysisTableVO> tables = new ArrayList<>();
        String typeName = typeName(formType);
        String title = personName != null && !personName.isBlank()
                ? personName + "的" + typeName + "明细（" + filtered.size() + " 条）"
                : typeName + "完整明细（" + filtered.size() + " 条）";

        List<Map<String, String>> columns = buildColumns(formType);
        tables.add(JijianAnalysisTableVO.builder()
                .title(title).columns(columns).rows(new ArrayList<>(filtered)).build());
        return tables;
    }

    private List<Map<String, String>> buildColumns(String formType) {
        List<Map<String, String>> cols = new ArrayList<>();
        cols.add(col("applicantName", "申请人"));
        cols.add(col("department", "部门"));
        if (JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)) {
            cols.add(col("overtimeStartTime", "加班开始时间"));
            cols.add(col("overtimeStartShift", "加班开始班次"));
            cols.add(col("overtimeEndTime", "加班结束时间"));
            cols.add(col("overtimeEndShift", "加班结束班次"));
            cols.add(col("compensatoryStartTime", "调休开始时间"));
            cols.add(col("compensatoryStartShift", "调休开始班次"));
            cols.add(col("compensatoryEndTime", "调休结束时间"));
            cols.add(col("compensatoryEndShift", "调休结束班次"));
            cols.add(col("compensatoryDuration", "调休时长(天)"));
            cols.add(col("isOutside", "是否出义"));
            cols.add(col("outsideLocation", "出义地址"));
        } else if (JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue().equals(formType)) {
            cols.add(col("leaveLocation", "疗休养地点"));
            cols.add(col("startTime", "开始时间"));
            cols.add(col("endTime", "结束时间"));
            cols.add(col("leaveDays", "天数"));
            cols.add(col("workYears", "工龄"));
        } else if (JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue().equals(formType)) {
            cols.add(col("tripReason", "出差事由"));
            cols.add(col("departurePlace", "出发地"));
            cols.add(col("destination", "目的地"));
            cols.add(col("startTime", "出差开始时间"));
            cols.add(col("endTime", "出差结束时间"));
            cols.add(col("leaveDays", "出差天数"));
            cols.add(col("tripPersonnel", "出差人员"));
            cols.add(col("tripPeopleCount", "出差人数"));
            cols.add(col("isOutside", "是否出义"));
            cols.add(col("outsideLocation", "出义地点"));
        } else {
            cols.add(col("leaveType", "假别"));
            cols.add(col("leaveReason", "事由"));
            cols.add(col("startTime", "开始时间"));
            cols.add(col("endTime", "结束时间"));
            cols.add(col("leaveDays", "天数"));
            cols.add(col("leaveStatus", "状态"));
        }
        cols.add(col("remark", "备注"));
        return cols;
    }

    private JijianDatabaseContextMetaVO buildMeta(String formType, int totalCount, int filteredCount, String timeRangeLabel) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put(formType, filteredCount);
        return JijianDatabaseContextMetaVO.builder()
                .tablesUsed(Collections.singletonList(formType))
                .rowCounts(counts)
                .dataSource("database")
                .sensitiveFieldsRemoved(false)
                .truncated(filteredCount > MAX_RECORDS_TO_DEEPSEEK)
                .timeRange(timeRangeLabel)
                .build();
    }

    private Map<String, String> buildSchema(String formType) {
        Map<String, String> schema = new LinkedHashMap<>();
        schema.put("applicantName", "申请人姓名");
        schema.put("employeeNo", "员工编号");
        schema.put("department", "部门");
        if (JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)) {
            schema.put("overtimeStartTime", "加班开始时间");
            schema.put("overtimeStartShift", "加班开始班次（上午/下午）");
            schema.put("overtimeEndTime", "加班结束时间");
            schema.put("overtimeEndShift", "加班结束班次");
            schema.put("compensatoryStartTime", "调休开始时间");
            schema.put("compensatoryStartShift", "调休开始班次");
            schema.put("compensatoryEndTime", "调休结束时间");
            schema.put("compensatoryEndShift", "调休结束班次");
            schema.put("compensatoryDuration", "调休时长（天）");
            schema.put("isOutside", "是否出义（出义/不出义）");
            schema.put("outsideLocation", "出义具体地址");
        } else if (JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue().equals(formType)) {
            schema.put("leaveLocation", "疗休养地点");
            schema.put("startTime", "开始时间");
            schema.put("endTime", "结束时间");
            schema.put("leaveDays", "疗休养天数");
            schema.put("workYears", "工龄");
            schema.put("startWorkTime", "参加工作时间");
        } else if (JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue().equals(formType)) {
            schema.put("tripReason", "出差事由");
            schema.put("departurePlace", "出发地");
            schema.put("destination", "目的地");
            schema.put("startTime", "出差开始时间");
            schema.put("endTime", "出差结束时间");
            schema.put("leaveDays", "出差天数");
            schema.put("tripPersonnel", "出差人员");
            schema.put("tripPeopleCount", "出差人数");
            schema.put("isOutside", "是否出义");
            schema.put("outsideLocation", "出义地点");
        } else {
            schema.put("leaveType", "假别类型");
            schema.put("leaveReason", "请假事由");
            schema.put("startTime", "开始时间");
            schema.put("endTime", "结束时间");
            schema.put("leaveDays", "天数");
            schema.put("leaveStatus", "状态");
            schema.put("isOutside", "是否外地");
            schema.put("outsideLocation", "地点");
        }
        schema.put("remark", "备注");
        return schema;
    }

    private String typeName(String formType) {
        if (JijianQueryFormTypeEnum.COMPENSATORY_LEAVE.getValue().equals(formType)) return "调休";
        if (JijianQueryFormTypeEnum.RECUPERATION_LEAVE.getValue().equals(formType)) return "疗休养";
        if (JijianQueryFormTypeEnum.PERSONAL_LEAVE.getValue().equals(formType)) return "事假";
        if (JijianQueryFormTypeEnum.BUSINESS_TRIP.getValue().equals(formType)) return "出差";
        return "记录";
    }

    private String nvl(String s) { return s == null ? "" : s; }
    private String nvlStr(Object o) { return o == null ? "" : o.toString(); }

    private String fmtDt(LocalDateTime dt) {
        if (dt == null) return "";
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private LocalDateTime businessTripStart(BusinessTripDO item) {
        return item.getStartDate() != null ? item.getStartDate() : item.getStartTime();
    }

    private LocalDateTime businessTripEnd(BusinessTripDO item) {
        return item.getEndDate() != null ? item.getEndDate() : item.getEndTime();
    }

    private double parseDuration(Object val) {
        if (val == null || val.toString().isBlank()) return 0;
        try { return Double.parseDouble(val.toString()); } catch (Exception e) { return 0; }
    }

    private Map<String, String> col(String key, String label) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("label", label);
        return m;
    }

    @Data
    public static class DirectAnalysisResult {
        private String formType;
        private String department;
        private String personName;
        private String timeRangeLabel;
        private LocalDate startDate;
        private LocalDate endDate;
        private int totalCount;
        private List<Map<String, Object>> filteredRecords;
        private List<JijianMetricVO> metrics;
        private List<JijianChartVO> charts;
        private List<JijianAnalysisTableVO> tables;
        private JijianDatabaseContextMetaVO databaseContextMeta;
        private Map<String, String> schema;
    }
}
