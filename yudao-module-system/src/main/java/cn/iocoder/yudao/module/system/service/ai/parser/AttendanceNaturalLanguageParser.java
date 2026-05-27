package cn.iocoder.yudao.module.system.service.ai.parser;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.system.service.ai.bo.AttendanceQueryConditionsBO;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 考勤日报表 - 自然语言规则解析器（当前阶段不接入大模型）
 */
@Component
public class AttendanceNaturalLanguageParser {

    private static final Pattern EMPLOYEE_NO_PATTERN = Pattern.compile("员工编号\\s*(\\d+)|工号\\s*(\\d+)");
    private static final Pattern DEPT_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}部)");
    private static final Pattern NAME_PATTERN = Pattern.compile("查询\\s*([\\u4e00-\\u9fa5]{2,4})(?=本月|上月|昨日|昨天|今日|今天|的|考勤|迟到|记录|$)");

    public AttendanceQueryConditionsBO parse(String message) {
        String text = StrUtil.trim(message);
        AttendanceQueryConditionsBO conditions = new AttendanceQueryConditionsBO();
        if (StrUtil.isBlank(text)) {
            return conditions;
        }

        parseDateRange(text, conditions);
        parseEmployeeNo(text, conditions);
        parseDept(text, conditions);
        parseClockResult(text, conditions);
        parseRemarkKeyword(text, conditions);
        parseEmployeeName(text, conditions);
        return conditions;
    }

    public String buildParseSummary(String message, AttendanceQueryConditionsBO conditions, int total) {
        List<String> parts = new ArrayList<>();
        parts.add("已识别数据表：考勤日报表(attendance_daily_report)");
        if (StrUtil.isNotBlank(conditions.getEmployeeName())) {
            parts.add("姓名 = " + conditions.getEmployeeName());
        }
        if (StrUtil.isNotBlank(conditions.getEmployeeNo())) {
            parts.add("员工编号 = " + conditions.getEmployeeNo());
        }
        if (StrUtil.isNotBlank(conditions.getDeptName())) {
            parts.add("部门 = " + conditions.getDeptName());
        }
        if (StrUtil.isNotBlank(conditions.getClockInResultLike())) {
            parts.add("上班打卡结果 LIKE %" + conditions.getClockInResultLike() + "%");
        }
        if (StrUtil.isNotBlank(conditions.getClockOutResultLike())) {
            parts.add("下班打卡结果 LIKE %" + conditions.getClockOutResultLike() + "%");
        }
        if (StrUtil.isNotBlank(conditions.getRemarkKeyword())) {
            parts.add("备注/地点关键词 = " + conditions.getRemarkKeyword());
        }
        if (conditions.getDateStart() != null && conditions.getDateEnd() != null) {
            parts.add("考勤日期范围 = " + conditions.getDateStart() + " ~ " + conditions.getDateEnd());
        }
        if (parts.size() == 1) {
            parts.add("未识别到额外筛选条件");
        }
        parts.add("共查询到 " + total + " 条记录");
        parts.add("（规则解析，原始问句：「" + message + "」）");
        return String.join("；", parts);
    }

    private void parseDateRange(String text, AttendanceQueryConditionsBO conditions) {
        LocalDate today = LocalDate.now();
        if (text.contains("今日") || text.contains("今天")) {
            conditions.setDateStart(today);
            conditions.setDateEnd(today);
            return;
        }
        if (text.contains("昨日") || text.contains("昨天")) {
            LocalDate yesterday = today.minusDays(1);
            conditions.setDateStart(yesterday);
            conditions.setDateEnd(yesterday);
            return;
        }
        if (text.contains("本月") || text.contains("这个月")) {
            conditions.setDateStart(today.withDayOfMonth(1));
            conditions.setDateEnd(today.withDayOfMonth(today.lengthOfMonth()));
            return;
        }
        if (text.contains("上月") || text.contains("上个月")) {
            LocalDate lastMonth = today.minusMonths(1);
            conditions.setDateStart(lastMonth.withDayOfMonth(1));
            conditions.setDateEnd(lastMonth.withDayOfMonth(lastMonth.lengthOfMonth()));
            return;
        }
        if (text.contains("上周")) {
            int dayOfWeek = today.getDayOfWeek().getValue();
            LocalDate end = today.minusDays(dayOfWeek);
            LocalDate start = end.minusDays(6);
            conditions.setDateStart(start);
            conditions.setDateEnd(end);
        }
    }

    private void parseEmployeeNo(String text, AttendanceQueryConditionsBO conditions) {
        Matcher matcher = EMPLOYEE_NO_PATTERN.matcher(text);
        if (matcher.find()) {
            String no = StrUtil.isNotBlank(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
            conditions.setEmployeeNo(no);
        }
    }

    private void parseDept(String text, AttendanceQueryConditionsBO conditions) {
        Matcher matcher = DEPT_PATTERN.matcher(text);
        if (matcher.find()) {
            conditions.setDeptName(matcher.group(1));
        }
    }

    private void parseClockResult(String text, AttendanceQueryConditionsBO conditions) {
        if (text.contains("迟到")) {
            conditions.setClockInResultLike("迟到");
        }
        if (text.contains("早退")) {
            conditions.setClockOutResultLike("早退");
        }
    }

    private void parseRemarkKeyword(String text, AttendanceQueryConditionsBO conditions) {
        if (text.contains("出差")) {
            conditions.setRemarkKeyword("出差");
        } else if (text.contains("事假")) {
            conditions.setRemarkKeyword("事假");
        }
    }

    private void parseEmployeeName(String text, AttendanceQueryConditionsBO conditions) {
        if (StrUtil.isNotBlank(conditions.getDeptName()) && text.contains(conditions.getDeptName())) {
            return;
        }
        Matcher matcher = NAME_PATTERN.matcher(text);
        if (!matcher.find()) {
            return;
        }
        String name = matcher.group(1);
        List<String> blocked = List.of("查询", "人事", "财务", "市场", "上班", "下班", "所有", "人员", "员工", "编号");
        if (!name.contains("部") && !blocked.contains(name)) {
            conditions.setEmployeeName(name);
        }
    }

}
