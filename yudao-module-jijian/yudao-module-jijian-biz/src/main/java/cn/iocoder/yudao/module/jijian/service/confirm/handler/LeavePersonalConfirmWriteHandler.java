package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal.LeavePersonalMapper;
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
 * 事假 确认写入 Handler。
 *
 * <p>修复：
 * <ul>
 *   <li>is_outside 按原文存储（出义/不出义/是/否），不再转 0/1；</li>
 *   <li>申请人姓名自动拆分：applicant_name 存纯姓名，employee_no 存编号；</li>
 *   <li>两步 parseDateTime，兼容纯日期格式；</li>
 *   <li>逐行收集错误，跳过空行。</li>
 * </ul>
 */
@Slf4j
@Component
public class LeavePersonalConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private LeavePersonalMapper leavePersonalMapper;

    @Override public String getFormType() { return FormTypeConstants.LEAVE_PERSONAL; }
    @Override public String getBusinessTableName() { return "jijian_leave_personal"; }

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        log.info("[JijianRuntimeMarker] 2026-06-fix-duty-fuzzy-v4 LeavePersonalConfirmWriteHandler loaded");
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
                String rawName = get(row, "申请人显示名", "申请人", "姓名", "员工姓名", "职工姓名");
                if (StrUtil.isBlank(rawName)) {
                    String biz = get(row, "请假类型", "请假天数", "请假开始时间", "是否出义");
                    if (StrUtil.isNotBlank(biz)) {
                        if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：申请人为空但存在业务字段");
                    } else {
                        skippedCount++;
                        if (skippedMessages.size() < 20) skippedMessages.add("第 " + rowNum + " 行：空白行，已跳过");
                    }
                    continue;
                }

                // 拆分姓名与工号
                JijianPersonNameUtils.ParseResult person = JijianPersonNameUtils.parse(rawName);
                String pureName  = (person != null && person.name != null) ? person.name : rawName;
                String parsedNo  = (person != null) ? person.employeeNo : null;
                // employee_no：优先用 Excel 中显式列，否则用从姓名中解析的值
                String explicitNo = get(row, "员工编号", "工号", "职工编号", "人员编号");
                String employeeNo = StrUtil.isNotBlank(explicitNo) ? explicitNo : parsedNo;

                String daysStr = get(row, "请假天数", "天数");
                BigDecimal days = null;
                if (StrUtil.isNotBlank(daysStr)) {
                    try { days = new BigDecimal(daysStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }

                // is_outside：直接按原文存储，不做 0/1 转换
                String isOutside = StrUtil.trimToNull(get(row, "是否出义", "出义", "是否外出", "外出"));

                String department = get(row, "部门", "所在部门", "申请部门");
                String leaveType = get(row, "请假类型", "假别", "类型");
                String leaveReason = get(row, "请假事由", "事由", "原因");
                LocalDateTime startTime = parseDateTime(get(row, "请假开始时间", "开始时间", "开始日期"), rowNum);
                LocalDateTime endTime = parseDateTime(get(row, "请假结束时间", "结束时间", "结束日期"), rowNum);
                String outsideLocation = get(row, "出义具体地点", "出义地点", "外出地点");
                String leaveStatus = get(row, "请假状态", "状态");
                String leaveMonth = get(row, "请假月份", "月份");
                String remark = get(row, "备注", "说明");
                if (existsSameBusinessData(department, pureName, employeeNo, leaveType, leaveReason,
                        startTime, endTime, days, isOutside, outsideLocation, leaveStatus, leaveMonth)) {
                    skippedCount++;
                    if (skippedMessages.size() < 20) {
                        skippedMessages.add("第 " + rowNum + " 行：数据库已存在相同事假数据，已跳过");
                    }
                    continue;
                }

                LeavePersonalDO entity = LeavePersonalDO.builder()
                        .department(department)
                        .applicantName(pureName)
                        .employeeNo(employeeNo)
                        .leaveType(leaveType)
                        .leaveReason(leaveReason)
                        .startTime(startTime)
                        .endTime(endTime)
                        .leaveDays(days)
                        .isOutside(isOutside)          // 原文存储
                        .outsideLocation(outsideLocation)
                        .leaveStatus(leaveStatus)
                        .leaveMonth(leaveMonth)
                        .remark(remark)
                        .sourceParsedDataId(parsedData.getId())
                        .build();

                leavePersonalMapper.insert(entity);
                if (entity.getId() != null) ids.add(entity.getId());

            } catch (ServiceException se) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：" + se.getMessage());
                log.warn("[LeavePersonalConfirmWrite] 第 {} 行业务异常：{}", rowNum, se.getMessage());
            } catch (Exception e) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：解析失败 - " + e.getMessage());
                log.error("[LeavePersonalConfirmWrite] 第 {} 行意外异常", rowNum, e);
            }
        }

        if (ids.isEmpty()) {
            if (skippedCount > 0 && failedMessages.isEmpty()) {
                log.info("[LeavePersonalConfirmWrite] 总行={} 成功=0 跳过={} 失败=0",
                        rows.size(), skippedCount);
                return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                        rows.size(), skippedCount, skippedMessages, 0, failedMessages);
            }
            String detail = failedMessages.isEmpty() ? "所有行均为空行"
                    : String.join("；", failedMessages.subList(0, Math.min(5, failedMessages.size())));
            throw new ServiceException(PARSED_DATA_REQUIRED_FIELD_MISSING.getCode(), "事假表全部行写入失败：" + detail);
        }

        log.info("[LeavePersonalConfirmWrite] 总行={} 成功={} 跳过={} 失败={}",
                rows.size(), ids.size(), skippedCount, failedMessages.size());
        return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages, failedMessages.size(), failedMessages);
    }

    private boolean existsSameBusinessData(String department, String applicantName, String employeeNo,
                                           String leaveType, String leaveReason, LocalDateTime startTime,
                                           LocalDateTime endTime, BigDecimal leaveDays, String isOutside,
                                           String outsideLocation, String leaveStatus, String leaveMonth) {
        LambdaQueryWrapper<LeavePersonalDO> wrapper = new LambdaQueryWrapper<LeavePersonalDO>()
                .eq(StrUtil.isNotBlank(department), LeavePersonalDO::getDepartment, department)
                .eq(LeavePersonalDO::getApplicantName, applicantName)
                .eq(StrUtil.isNotBlank(employeeNo), LeavePersonalDO::getEmployeeNo, employeeNo)
                .eq(StrUtil.isNotBlank(leaveType), LeavePersonalDO::getLeaveType, leaveType)
                .eq(StrUtil.isNotBlank(leaveReason), LeavePersonalDO::getLeaveReason, leaveReason)
                .eq(startTime != null, LeavePersonalDO::getStartTime, startTime)
                .eq(endTime != null, LeavePersonalDO::getEndTime, endTime)
                .eq(leaveDays != null, LeavePersonalDO::getLeaveDays, leaveDays)
                .eq(StrUtil.isNotBlank(isOutside), LeavePersonalDO::getIsOutside, isOutside)
                .eq(StrUtil.isNotBlank(outsideLocation), LeavePersonalDO::getOutsideLocation, outsideLocation)
                .eq(StrUtil.isNotBlank(leaveStatus), LeavePersonalDO::getLeaveStatus, leaveStatus)
                .eq(StrUtil.isNotBlank(leaveMonth), LeavePersonalDO::getLeaveMonth, leaveMonth);
        return leavePersonalMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LeavePersonalDO> list = leavePersonalMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LeavePersonalDO d : list) {
            result.add(toSummaryMap("申请人", d.getApplicantName(), "部门", d.getDepartment(),
                    "请假类型", d.getLeaveType(), "天数",
                    d.getLeaveDays() == null ? null : d.getLeaveDays().toPlainString(),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private LocalDateTime parseDateTime(String s, int rowNum) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return DateUtil.parseLocalDateTime(s);
        } catch (Exception e1) {
            try {
                return DateUtil.parse(s).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[LeavePersonalConfirmWrite] 第 {} 行日期格式无法识别，值=「{}」", rowNum, s);
                return null;
            }
        }
    }
}
