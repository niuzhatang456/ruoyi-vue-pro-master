package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth.LeaveHealthMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import cn.iocoder.yudao.module.jijian.util.JijianPersonNameUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

/**
 * 疗休养请假 确认写入 Handler。
 *
 * <p>修复点：
 * <ul>
 *   <li>parseDateTime 双阶段：先 parseLocalDateTime，失败则 parse().toLocalDateTime()；解决纯日期格式 NULL 问题；</li>
 *   <li>支持年月格式（"2021-09"）→ 取当月 1 日；</li>
 *   <li>逐行收集错误，不再首行异常全量回滚；</li>
 *   <li>日志详细输出行号和字段名，便于排查。</li>
 * </ul>
 */
@Slf4j
@Component
public class LeaveHealthConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource
    private LeaveHealthMapper leaveHealthMapper;

    @Override
    public String getFormType() { return FormTypeConstants.LEAVE_HEALTH; }

    @Override
    public String getBusinessTableName() { return "jijian_leave_health"; }

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);

        List<Long> ids = new ArrayList<>(rows.size());
        List<String> failedMessages = new ArrayList<>();
        List<String> skippedMessages = new ArrayList<>();
        int skippedCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 1;
            try {
                String rawName = get(row, "申请人", "姓名", "员工姓名", "职工姓名", "申请人显示名");
                // 拆分姓名与工号
                JijianPersonNameUtils.ParseResult personParsed = JijianPersonNameUtils.parse(rawName);
                String name = (personParsed != null && personParsed.name != null) ? personParsed.name : rawName;
                if (StrUtil.isBlank(name)) {
                    String businessVal = get(row,
                            "疗养假开始时间", "开始时间", "请假天数", "天数", "工作年限", "疗休养地点");
                    if (StrUtil.isNotBlank(businessVal)) {
                        if (failedMessages.size() < 20) {
                            failedMessages.add("第 " + rowNum + " 行：申请人/姓名为空，但存在业务字段，疑似合并单元格");
                        }
                        log.warn("[LeaveHealthConfirmWrite] 第 {} 行：有业务字段但姓名为空，行摘要：{}", rowNum, rowSummary(row));
                    } else {
                        skippedCount++;
                        if (skippedMessages.size() < 20) {
                            skippedMessages.add("第 " + rowNum + " 行：空白行或合计行，已跳过");
                        }
                    }
                    continue;
                }

                String daysStr = get(row, "请假天数", "天数");
                BigDecimal days = null;
                if (StrUtil.isNotBlank(daysStr)) {
                    try { days = new BigDecimal(daysStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }

                // employee_no：优先显式列，否则取从姓名拆出的编号
                String explicitNo = get(row, "员工编号", "工号", "职工编号");
                String parsedNo   = (personParsed != null) ? personParsed.employeeNo : null;
                String employeeNo = StrUtil.isNotBlank(explicitNo) ? explicitNo : parsedNo;

                LeaveHealthDO entity = LeaveHealthDO.builder()
                        .department(get(row, "部门", "所在部门", "科室"))
                        .applicantName(name)
                        .employeeNo(employeeNo)
                        .leaveLocation(get(row, "休假地点", "疗休养地点", "地点"))
                        .startTime(parseDateTime(get(row, "疗养假开始时间", "开始时间", "请假开始时间", "休假开始时间"), rowNum, "疗养假开始时间", failedMessages))
                        .endTime(parseDateTime(get(row, "疗养假结束时间", "结束时间", "请假结束时间", "休假结束时间"), rowNum, "疗养假结束时间", failedMessages))
                        .leaveDays(days)
                        .workYears(get(row, "工作年限", "工龄"))
                        .startWorkTime(parseDateTimeOrYearMonth(get(row, "参加工作时间", "参加工作年月", "工作开始时间"), rowNum, failedMessages))
                        .remark(get(row, "备注", "说明"))
                        .sourceParsedDataId(parsedData.getId())
                        .build();

                if (existsSameBusinessData(entity)) {
                    skippedCount++;
                    if (skippedMessages.size() < 20) {
                        skippedMessages.add("绗?" + rowNum + " 琛岋細鏁版嵁搴撳凡瀛樺湪鐩稿悓鐤椾紤鍏昏鍋囨暟鎹紝宸茶烦杩?");
                    }
                    continue;
                }

                leaveHealthMapper.insert(entity);
                if (entity.getId() != null) ids.add(entity.getId());

            } catch (ServiceException se) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：" + se.getMessage());
                log.warn("[LeaveHealthConfirmWrite] 第 {} 行业务异常：{}", rowNum, se.getMessage());
            } catch (Exception e) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：解析失败 - " + e.getMessage());
                log.error("[LeaveHealthConfirmWrite] 第 {} 行意外异常", rowNum, e);
            }
        }

        if (ids.isEmpty()) {
            if (skippedCount > 0 && failedMessages.isEmpty()) {
                log.info("[LeaveHealthConfirmWrite] 鎬昏={} 鎴愬姛=0 璺宠繃={} 澶辫触=0",
                        rows.size(), skippedCount);
                return ConfirmWriteResult.ofWithStats(
                        getFormType(), getBusinessTableName(), ids,
                        rows.size(), skippedCount, skippedMessages,
                        0, failedMessages);
            }
            int shown = Math.min(failedMessages.size(), 5);
            String detail = failedMessages.isEmpty() ? "所有行均为空行或汇总行"
                    : String.join("；", failedMessages.subList(0, shown))
                    + (failedMessages.size() > 5 ? "……（共 " + failedMessages.size() + " 处错误）" : "");
            throw new ServiceException(PARSED_DATA_REQUIRED_FIELD_MISSING.getCode(),
                    "疗休养表全部行写入失败：" + detail);
        }

        log.info("[LeaveHealthConfirmWrite] 写入完成：总行={} 成功={} 跳过={} 失败={}",
                rows.size(), ids.size(), skippedCount, failedMessages.size());

        return ConfirmWriteResult.ofWithStats(
                getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages,
                failedMessages.size(), failedMessages);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LeaveHealthDO> list = leaveHealthMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LeaveHealthDO d : list) {
            result.add(toSummaryMap(
                    "申请人", d.getApplicantName(),
                    "部门", d.getDepartment(),
                    "休假地点", d.getLeaveLocation(),
                    "天数", d.getLeaveDays() == null ? null : d.getLeaveDays().toPlainString(),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    // ───────────────────────────────────────────────────────────
    // 工具方法
    // ───────────────────────────────────────────────────────────

    /**
     * 两阶段日期解析：
     * 1. DateUtil.parseLocalDateTime(s)  — 处理标准 datetime 格式
     * 2. DateUtil.parse(s).toLocalDateTime() — 处理纯日期、中文日期等更多格式
     * 两步均失败时记录警告并返回 null（时间字段非强制必填）。
     */
    private boolean existsSameBusinessData(LeaveHealthDO value) {
        LambdaQueryWrapper<LeaveHealthDO> wrapper = new LambdaQueryWrapper<LeaveHealthDO>()
                .eq(StrUtil.isNotBlank(value.getDepartment()), LeaveHealthDO::getDepartment, value.getDepartment())
                .eq(LeaveHealthDO::getApplicantName, value.getApplicantName())
                .eq(StrUtil.isNotBlank(value.getEmployeeNo()), LeaveHealthDO::getEmployeeNo, value.getEmployeeNo())
                .eq(StrUtil.isNotBlank(value.getLeaveLocation()), LeaveHealthDO::getLeaveLocation, value.getLeaveLocation())
                .eq(value.getStartTime() != null, LeaveHealthDO::getStartTime, value.getStartTime())
                .eq(value.getEndTime() != null, LeaveHealthDO::getEndTime, value.getEndTime())
                .eq(value.getLeaveDays() != null, LeaveHealthDO::getLeaveDays, value.getLeaveDays())
                .eq(StrUtil.isNotBlank(value.getWorkYears()), LeaveHealthDO::getWorkYears, value.getWorkYears())
                .eq(value.getStartWorkTime() != null, LeaveHealthDO::getStartWorkTime, value.getStartWorkTime())
                .eq(StrUtil.isNotBlank(value.getRemark()), LeaveHealthDO::getRemark, value.getRemark());
        return leaveHealthMapper.selectCount(wrapper) > 0;
    }

    private LocalDateTime parseDateTime(String s, int rowNum, String fieldName, List<String> rowErrors) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return DateUtil.parseLocalDateTime(s);
        } catch (Exception e1) {
            try {
                return DateUtil.parse(s).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[LeaveHealthConfirmWrite] 第 {} 行字段「{}」日期格式无法识别，原始值=「{}」，将存为 null",
                        rowNum, fieldName, s);
                return null;
            }
        }
    }

    /**
     * 支持"参加工作时间"可能为年月格式（如"2021-09"或"2021年9月"）→ 取当月 1 日。
     */
    private LocalDateTime parseDateTimeOrYearMonth(String s, int rowNum, List<String> rowErrors) {
        if (StrUtil.isBlank(s)) return null;
        // 先尝试正常解析
        LocalDateTime result = parseDateTime(s, rowNum, "参加工作时间", rowErrors);
        if (result != null) return result;
        // 再尝试 "yyyy-MM" 或 "yyyy/MM" 或 "yyyy年M月" 格式
        try {
            String digits = s.replaceAll("[^0-9]", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
            String[] parts = digits.split("-");
            if (parts.length >= 2) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                if (year >= 1970 && year <= 2100 && month >= 1 && month <= 12) {
                    log.info("[LeaveHealthConfirmWrite] 第 {} 行「参加工作时间」年月格式=「{}」，解析为 {}-{}-01", rowNum, s, year, month);
                    return LocalDateTime.of(year, month, 1, 0, 0, 0);
                }
            }
        } catch (Exception ex) {
            log.warn("[LeaveHealthConfirmWrite] 第 {} 行「参加工作时间」年月解析也失败，原始值=「{}」", rowNum, s);
        }
        return null;
    }

    private String rowSummary(Map<String, String> row) {
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (StrUtil.isNotBlank(e.getValue()) && count < 2) {
                sb.append(e.getKey()).append("=[").append(StrUtil.maxLength(e.getValue(), 8)).append("] ");
                count++;
            }
        }
        return sb.toString().trim();
    }
}
