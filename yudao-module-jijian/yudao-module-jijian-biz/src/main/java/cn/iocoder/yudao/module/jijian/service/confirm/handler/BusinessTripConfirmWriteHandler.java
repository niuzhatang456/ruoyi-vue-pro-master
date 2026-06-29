package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.dal.dataobject.businesstrip.BusinessTripDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.businesstrip.BusinessTripMapper;
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
 * 出差 确认写入 Handler（完全重写，按用户要求字段映射）。
 *
 * <p>字段映射：
 * <pre>
 *   部门             → department
 *   申请人显示名     → applicant_name（纯姓名）
 *   员工编号         → employee_no
 *   出差事由         → trip_reason
 *   出发地           → departure_place
 *   目的地           → destination
 *   出差开始日期     → start_date
 *   出差结束日期     → end_date
 *   出差天数         → trip_days
 *   出差人员         → trip_personnel
 *   是否出义         → is_outside（原文）
 * </pre>
 */
@Slf4j
@Component
public class BusinessTripConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private BusinessTripMapper businessTripMapper;

    @Override public String getFormType() { return FormTypeConstants.BUSINESS_TRIP; }
    @Override public String getBusinessTableName() { return "jijian_business_trip"; }

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
                String rawName = get(row, "申请人显示名", "申请人", "姓名", "员工姓名", "职工姓名");
                if (StrUtil.isBlank(rawName)) {
                    String biz = get(row, "出差事由", "出发地", "目的地", "出差开始日期", "出差天数");
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
                String explicitNo = get(row, "员工编号", "工号", "职工编号", "人员编号");
                String employeeNo = StrUtil.isNotBlank(explicitNo) ? explicitNo : parsedNo;

                // 出差天数
                String daysStr = get(row, "出差天数", "天数", "出差时长");
                BigDecimal tripDays = null;
                if (StrUtil.isNotBlank(daysStr)) {
                    try { tripDays = new BigDecimal(daysStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }

                // 出差开始/结束日期
                LocalDateTime startDate = parseDateTime(
                        get(row, "出差开始日期", "开始日期", "出发日期", "出差开始时间", "开始时间"),
                        rowNum, "出差开始日期");
                LocalDateTime endDate = parseDateTime(
                        get(row, "出差结束日期", "结束日期", "返回日期", "出差结束时间", "结束时间"),
                        rowNum, "出差结束日期");

                // 出发地 / 目的地 / 出差事由 / 出差人员（主要字段，有值则填，无则记录日志）
                String tripReason    = get(row, "出差事由", "事由", "出差原因", "原因");
                String departPlace   = get(row, "出发地", "出发地点", "起点");
                String dest          = get(row, "目的地", "出差地点", "到达地", "终点", "出差目的地");
                String tripPersonnel = get(row, "出差人员", "随行人员", "人员");
                Integer tripPeopleCount = parsePeopleCount(get(row, "出差人数", "人数", "同行人数"), tripPersonnel);
                String isOutside     = StrUtil.trimToNull(get(row, "是否出义", "出义", "是否外出", "外出"));

                // 字段映射校验日志（警告，不中断写入）
                if (startDate == null && StrUtil.isNotBlank(get(row, "出差开始日期", "开始日期", "出发日期"))) {
                    log.warn("[BusinessTripConfirmWrite] 第 {} 行：出差开始日期有值但解析为空", rowNum);
                }
                if (StrUtil.isBlank(dest) && StrUtil.isBlank(tripReason)) {
                    log.info("[BusinessTripConfirmWrite] 第 {} 行：目的地和出差事由均为空，将按空值写入", rowNum);
                }

                BusinessTripDO entity = BusinessTripDO.builder()
                        .department(get(row, "部门", "所属部门", "申请部门"))
                        .applicantName(pureName)
                        .employeeNo(employeeNo)
                        .tripReason(tripReason)
                        .departurePlace(departPlace)
                        .destination(dest)
                        .startDate(startDate)
                        .endDate(endDate)
                        .tripDays(tripDays)
                        .tripPersonnel(tripPersonnel)
                        .tripPeopleCount(tripPeopleCount)
                        .isOutside(isOutside)
                        .outsideLocation(get(row, "出义具体地点", "出义地点", "外出地点"))
                        .remark(get(row, "备注", "说明"))
                        .sourceParsedDataId(parsedData.getId())
                        .build();

                if (existsSameBusinessData(entity)) {
                    skippedCount++;
                    if (skippedMessages.size() < 20) {
                        skippedMessages.add("绗?" + rowNum + " 琛岋細鏁版嵁搴撳凡瀛樺湪鐩稿悓鍑哄樊鏁版嵁锛屽凡璺宠繃");
                    }
                    continue;
                }

                businessTripMapper.insert(entity);
                if (entity.getId() != null) ids.add(entity.getId());

            } catch (ServiceException se) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：" + se.getMessage());
                log.warn("[BusinessTripConfirmWrite] 第 {} 行业务异常：{}", rowNum, se.getMessage());
            } catch (Exception e) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：解析失败 - " + e.getMessage());
                log.error("[BusinessTripConfirmWrite] 第 {} 行意外异常", rowNum, e);
            }
        }

        if (ids.isEmpty()) {
            if (skippedCount > 0 && failedMessages.isEmpty()) {
                log.info("[BusinessTripConfirmWrite] 鎬昏={} 鎴愬姛=0 璺宠繃={} 澶辫触=0",
                        rows.size(), skippedCount);
                return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                        rows.size(), skippedCount, skippedMessages, 0, failedMessages);
            }
            String detail = failedMessages.isEmpty() ? "所有行均为空行"
                    : String.join("；", failedMessages.subList(0, Math.min(5, failedMessages.size())));
            throw new ServiceException(PARSED_DATA_REQUIRED_FIELD_MISSING.getCode(), "出差表全部行写入失败：" + detail);
        }

        log.info("[BusinessTripConfirmWrite] 总行={} 成功={} 跳过={} 失败={}",
                rows.size(), ids.size(), skippedCount, failedMessages.size());
        return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages, failedMessages.size(), failedMessages);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<BusinessTripDO> list = businessTripMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (BusinessTripDO d : list) {
            result.add(toSummaryMap("申请人", d.getApplicantName(), "部门", d.getDepartment(),
                    "目的地", d.getDestination(), "出差事由", d.getTripReason(),
                    "天数", d.getTripDays() == null ? null : d.getTripDays().toPlainString(),
                    "出差人数", d.getTripPeopleCount() == null ? null : String.valueOf(d.getTripPeopleCount()),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private boolean existsSameBusinessData(BusinessTripDO value) {
        LambdaQueryWrapper<BusinessTripDO> wrapper = new LambdaQueryWrapper<BusinessTripDO>()
                .eq(StrUtil.isNotBlank(value.getDepartment()), BusinessTripDO::getDepartment, value.getDepartment())
                .eq(BusinessTripDO::getApplicantName, value.getApplicantName())
                .eq(StrUtil.isNotBlank(value.getEmployeeNo()), BusinessTripDO::getEmployeeNo, value.getEmployeeNo())
                .eq(StrUtil.isNotBlank(value.getTripReason()), BusinessTripDO::getTripReason, value.getTripReason())
                .eq(StrUtil.isNotBlank(value.getDeparturePlace()), BusinessTripDO::getDeparturePlace, value.getDeparturePlace())
                .eq(StrUtil.isNotBlank(value.getDestination()), BusinessTripDO::getDestination, value.getDestination())
                .eq(value.getStartDate() != null, BusinessTripDO::getStartDate, value.getStartDate())
                .eq(value.getEndDate() != null, BusinessTripDO::getEndDate, value.getEndDate())
                .eq(value.getTripDays() != null, BusinessTripDO::getTripDays, value.getTripDays())
                .eq(StrUtil.isNotBlank(value.getTripPersonnel()), BusinessTripDO::getTripPersonnel, value.getTripPersonnel())
                .eq(value.getTripPeopleCount() != null, BusinessTripDO::getTripPeopleCount, value.getTripPeopleCount())
                .eq(StrUtil.isNotBlank(value.getIsOutside()), BusinessTripDO::getIsOutside, value.getIsOutside())
                .eq(StrUtil.isNotBlank(value.getOutsideLocation()), BusinessTripDO::getOutsideLocation, value.getOutsideLocation());
        return businessTripMapper.selectCount(wrapper) > 0;
    }

    private Integer parsePeopleCount(String explicitCount, String tripPersonnel) {
        if (StrUtil.isNotBlank(explicitCount)) {
            try {
                String digits = explicitCount.replaceAll("[^0-9]", "");
                if (StrUtil.isNotBlank(digits)) {
                    return Integer.parseInt(digits);
                }
            } catch (Exception ignored) {}
        }
        if (StrUtil.isBlank(tripPersonnel)) {
            return null;
        }
        String normalized = tripPersonnel.replace("、", ",").replace("，", ",").replace(";", ",").replace("；", ",");
        int count = 0;
        for (String token : normalized.split(",")) {
            if (StrUtil.isNotBlank(token)) {
                count++;
            }
        }
        return count > 0 ? count : null;
    }

    private LocalDateTime parseDateTime(String s, int rowNum, String fieldName) {
        if (StrUtil.isBlank(s)) return null;
        try {
            return DateUtil.parseLocalDateTime(s);
        } catch (Exception e1) {
            try {
                return DateUtil.parse(s).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[BusinessTripConfirmWrite] 第 {} 行字段「{}」日期格式无法识别，值=「{}」", rowNum, fieldName, s);
                return null;
            }
        }
    }
}
