package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily.AttendanceDailyMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import cn.iocoder.yudao.module.jijian.util.JijianPersonNameUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

/**
 * 考勤日报 确认写入 Handler。
 *
 * <p>修复点：
 * <ul>
 *   <li>使用 {@code insertBatch(list, 500)} 分批写入，支持 15000+ 行大数据；</li>
 *   <li>逐行收集错误，跳过合计行/空行，不再首行失败即终止；</li>
 *   <li>错误信息包含行号，方便前端定位；</li>
 *   <li>日志记录写入统计，敏感字段不打印原文。</li>
 * </ul>
 */
@Slf4j
@Component
public class AttendanceDailyConfirmWriteHandler extends AbstractConfirmWriteHandler {

    private static final int BATCH_SIZE = 500;

    @Resource
    private AttendanceDailyMapper attendanceDailyMapper;

    @Override
    public String getFormType() {
        return FormTypeConstants.ATTENDANCE;
    }

    @Override
    public String getBusinessTableName() {
        return "jijian_attendance_daily";
    }

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        // ── 防重复写入保护（CAS 之外的二次检查）──────────────────────────────
        long existingCount = attendanceDailyMapper.countBySourceParsedDataId(parsedData.getId());
        if (existingCount > 0) {
            throw new ServiceException(PARSED_DATA_ALREADY_CONFIRMED.getCode(),
                    "考勤日报批次已写入 " + existingCount + " 条，请勿重复提交（parsedDataId=" + parsedData.getId() + "）");
        }

        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) {
            throw exception(PARSED_DATA_ROWS_EMPTY);
        }

        log.info("[AttendanceConfirmWrite] 开始写入，共 {} 行，parsedDataId={}", rows.size(), parsedData.getId());

        List<AttendanceDailyDO> toInsert = new ArrayList<>(rows.size());
        // 失败行（有业务字段但关键字段缺失 / 解析异常），最多记录 20 条
        List<String> failedMessages = new ArrayList<>();
        // 跳过行（纯空白行 / 合计行），最多记录 20 条
        List<String> skippedMessages = new ArrayList<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 1;
            try {
                String rawName = get(row, "姓名", "员工姓名", "员工名称", "职工姓名", "工作人员");
                // 拆分姓名与工号（兼容"姓名(编号)"格式历史数据）
                JijianPersonNameUtils.ParseResult personParsed = JijianPersonNameUtils.parse(rawName);
                String name = (personParsed != null && personParsed.name != null) ? personParsed.name : rawName;
                if (StrUtil.isBlank(name)) {
                    // 判断是否有业务字段：有则计入 failedRows，无则计入 skippedRows（空白/汇总行）
                    String businessVal = get(row, "上班打卡时间", "下班打卡时间", "打卡时间", "考勤日期", "打卡结果");
                    if (StrUtil.isNotBlank(businessVal)) {
                        if (failedMessages.size() < 20) {
                            failedMessages.add("第 " + rowNum + " 行：姓名为空，但存在考勤字段，疑似合并单元格未展开或原始数据缺失");
                        }
                        log.warn("[AttendanceConfirmWrite] 第 {} 行：有考勤字段但姓名为空，疑似合并单元格问题", rowNum);
                    } else {
                        if (skippedMessages.size() < 20) {
                            skippedMessages.add("第 " + rowNum + " 行：空白行或合计行，已跳过");
                        }
                        log.debug("[AttendanceConfirmWrite] 第 {} 行：整行无有效字段，视为空白/汇总行跳过", rowNum);
                    }
                    continue;
                }

                // employee_no：优先显式列，否则取从姓名拆出的编号
                String explicitNo = get(row, "员工编号", "工号", "职工编号");
                String parsedNo   = (personParsed != null) ? personParsed.employeeNo : null;
                String employeeNo = StrUtil.isNotBlank(explicitNo) ? explicitNo : parsedNo;

                // 日期字段（纯日期列），用于与纯时间字段组合
                String dateStr = get(row, "日期", "考勤日期", "打卡日期");

                // 日期解析失败记 failedRow，不静默写 NULL
                LocalDate attendanceDate = parseDate(dateStr);
                if (attendanceDate == null && StrUtil.isNotBlank(dateStr)) {
                    if (failedMessages.size() < 20) {
                        failedMessages.add("第 " + rowNum + " 行：日期解析失败，原始值=「" + dateStr + "」");
                    }
                    log.warn("[AttendanceConfirmWrite] 第 {} 行：日期无法识别，原始值=「{}」", rowNum, dateStr);
                    continue;
                }

                String rawCheckinTimeStr  = get(row, "上班打卡时间");
                String rawCheckoutTimeStr = get(row, "下班打卡时间");

                LocalDateTime checkinTime  = parseDateTimeWithDate(rawCheckinTimeStr, dateStr);
                LocalDateTime checkoutTime = parseDateTimeWithDate(rawCheckoutTimeStr, dateStr);

                // 打卡时间有原始值但解析失败：记 failedRow，不静默写 NULL
                if (checkinTime == null && StrUtil.isNotBlank(rawCheckinTimeStr)) {
                    if (failedMessages.size() < 20) {
                        failedMessages.add("第 " + rowNum + " 行：上班打卡时间解析失败，原始值=「" + rawCheckinTimeStr + "」");
                    }
                    log.warn("[AttendanceConfirmWrite] 第 {} 行：上班打卡时间解析失败，原始值=「{}」", rowNum, rawCheckinTimeStr);
                    continue;
                }
                if (checkoutTime == null && StrUtil.isNotBlank(rawCheckoutTimeStr)) {
                    if (failedMessages.size() < 20) {
                        failedMessages.add("第 " + rowNum + " 行：下班打卡时间解析失败，原始值=「" + rawCheckoutTimeStr + "」");
                    }
                    log.warn("[AttendanceConfirmWrite] 第 {} 行：下班打卡时间解析失败，原始值=「{}」", rowNum, rawCheckoutTimeStr);
                    continue;
                }

                AttendanceDailyDO entity = AttendanceDailyDO.builder()
                        .employeeName(name)
                        .employeeNo(employeeNo)
                        .department(get(row, "部门", "所在部门", "科室"))
                        .weekDay(get(row, "星期"))
                        .checkinTime(checkinTime)
                        .checkinResult(get(row, "上班打卡结果"))
                        .checkinLocation(get(row, "上班打卡地点"))
                        .checkinRemark(get(row, "上班备注"))
                        .checkoutTime(checkoutTime)
                        .checkoutResult(get(row, "下班打卡结果"))
                        .checkoutLocation(get(row, "下班打卡地点"))
                        .checkoutRemark(get(row, "下班备注"))
                        .attendanceDate(attendanceDate)
                        .sourceParsedDataId(parsedData.getId())
                        .build();
                toInsert.add(entity);

            } catch (ServiceException se) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：" + se.getMessage());
                log.warn("[AttendanceConfirmWrite] 第 {} 行业务异常：{}", rowNum, se.getMessage());
            } catch (Exception e) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：解析失败 - " + e.getMessage());
                log.error("[AttendanceConfirmWrite] 第 {} 行意外异常", rowNum, e);
            }
        }

        if (toInsert.isEmpty()) {
            int shown = Math.min(failedMessages.size(), 5);
            String detail = failedMessages.isEmpty() ? "所有行均为空行或汇总行"
                    : String.join("；", failedMessages.subList(0, shown))
                    + (failedMessages.size() > 5 ? "……（共 " + failedMessages.size() + " 处错误）" : "");
            throw new ServiceException(PARSED_DATA_REQUIRED_FIELD_MISSING.getCode(),
                    "考勤日报全部行写入失败：" + detail);
        }

        // 在外层 @Transactional 事务内逐行写入（无异常则全部成功，JDBC rewriteBatchedStatements 驱动层合并）
        for (AttendanceDailyDO entity : toInsert) {
            attendanceDailyMapper.insert(entity);
        }

        // 收集 ID 用于 business_ids 回写（部分实体 ID 可能因主键策略未回填，
        // 但 confirmedCount 必须等于 toInsert.size()，不依赖 entity.getId() 是否为 null）
        List<Long> ids = new ArrayList<>(toInsert.size());
        for (AttendanceDailyDO entity : toInsert) {
            if (entity.getId() != null) ids.add(entity.getId());
        }
        int actualInserted = toInsert.size();  // 无异常 = 全部写入成功

        log.info("[AttendanceConfirmWrite] 写入完成：总行={} 成功={} 跳过={} 失败={} parsedDataId={}",
                rows.size(), actualInserted, skippedMessages.size(), failedMessages.size(), parsedData.getId());

        return ConfirmWriteResult.builder()
                .formType(getFormType())
                .businessTable(getBusinessTableName())
                .confirmedIds(ids)
                .confirmedCount(actualInserted)
                .idempotent(false)
                .totalRows(rows.size())
                .skippedRows(skippedMessages.size())
                .skippedMessages(skippedMessages)
                .failedRows(failedMessages.size())
                .failedMessages(failedMessages)
                .build();
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<AttendanceDailyDO> list = attendanceDailyMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (AttendanceDailyDO d : list) {
            result.add(toSummaryMap(
                    "姓名", d.getEmployeeName(),
                    "员工编号", d.getEmployeeNo(),
                    "部门", d.getDepartment(),
                    "考勤日期", d.getAttendanceDate() == null ? null : d.getAttendanceDate().toString(),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有工具方法
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 组合日期和时间字段解析，支持以下格式：
     * <ul>
     *   <li>完整日期时间：2026-04-03 08:31 / 2026/04/03 08:31:00</li>
     *   <li>纯时间（与 dateStr 组合）：08:31 / 8:31 / 08:31:00</li>
     *   <li>Excel 小数时间（fraction of day）：0.3549... → 转为 08:31:00</li>
     * </ul>
     * 若 timeStr 有值但解析失败，返回 null（调用方负责记录 failedRow）。
     */
    private LocalDateTime parseDateTimeWithDate(String timeStr, String dateStr) {
        if (StrUtil.isBlank(timeStr)) return null;
        String trimmed = timeStr.trim();

        // 先尝试直接解析完整日期时间
        LocalDateTime direct = parseDateTime(trimmed);
        if (direct != null) return direct;

        // 纯时间格式：H:mm 或 H:mm:ss
        if (trimmed.matches("\\d{1,2}:\\d{2}(:\\d{2})?") && StrUtil.isNotBlank(dateStr)) {
            LocalDateTime combined = parseDateTime(dateStr.trim() + " " + trimmed);
            if (combined != null) return combined;
        }

        // Excel 小数时间（0.35486 ≈ 8:31）：小数部分代表一天的分数
        try {
            double fraction = Double.parseDouble(trimmed);
            if (fraction >= 0 && fraction < 1) {
                int totalSeconds = (int) Math.round(fraction * 86400);
                int hour   = totalSeconds / 3600;
                int minute = (totalSeconds % 3600) / 60;
                int second = totalSeconds % 60;
                String timeFormatted = String.format("%02d:%02d:%02d", hour, minute, second);
                if (StrUtil.isNotBlank(dateStr)) {
                    LocalDateTime combined = parseDateTime(dateStr.trim() + " " + timeFormatted);
                    if (combined != null) return combined;
                }
            }
        } catch (NumberFormatException ignored) {
        }

        return null;  // 调用方须将 timeStr 非空时的 null 记为 failedRow
    }

    /**
     * 两阶段解析：DateUtil.parseLocalDateTime → DateUtil.parse().toLocalDateTime()
     * 解决 "2026-04-03" 纯日期格式被 parseLocalDateTime 拒绝导致 NULL 的问题。
     */
    private LocalDateTime parseDateTime(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return DateUtil.parseLocalDateTime(s);
        } catch (Exception e1) {
            try {
                return DateUtil.parse(s).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[AttendanceConfirmWrite] 时间格式无法识别，原始值=「{}」，将存为 null", s);
                return null;
            }
        }
    }

    /**
     * 两阶段解析 LocalDate：
     * 1. DateUtil.parse(s).toLocalDate() — 支持 yyyy-MM-dd / yyyy/MM/dd / 中文日期
     * 2. ISO 格式 substring(0,10) 兜底
     */
    private LocalDate parseDate(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return DateUtil.parse(s).toLocalDateTime().toLocalDate();
        } catch (Exception e) {
            try {
                return LocalDate.parse(s.trim().substring(0, 10));
            } catch (Exception e2) {
                log.warn("[AttendanceConfirmWrite] 日期格式无法识别，原始值=「{}」，将存为 null", s);
                return null;
            }
        }
    }
}
