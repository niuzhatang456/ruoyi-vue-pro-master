package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth.LeaveHealthMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class LeaveHealthConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private LeaveHealthMapper leaveHealthMapper;

    @Override public String getFormType() { return FormTypeConstants.LEAVE_HEALTH; }

    @Override
    public String getBusinessTableName() {{ return "jijian_leave_health"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = get(row, "申请人", "姓名");
            if (StrUtil.isBlank(name)) throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            String daysStr = get(row, "请假天数", "天数");
            BigDecimal days = null;
            if (StrUtil.isNotBlank(daysStr)) {
                try { days = new BigDecimal(daysStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
            }
            LeaveHealthDO do_ = LeaveHealthDO.builder()
                    .department(get(row, "部门"))
                    .applicantName(name)
                    .employeeNo(get(row, "员工编号", "工号"))
                    .leaveLocation(get(row, "休假地点"))
                    .startTime(parseDateTime(get(row, "疗养假开始时间", "开始时间")))
                    .endTime(parseDateTime(get(row, "疗养假结束时间", "结束时间")))
                    .leaveDays(days)
                    .workYears(get(row, "工作年限"))
                    .startWorkTime(parseDateTime(get(row, "参加工作时间")))
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            leaveHealthMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LeaveHealthDO> list = leaveHealthMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LeaveHealthDO d : list) {
            result.add(toSummaryMap("申请人", d.getApplicantName(), "部门", d.getDepartment(),
                "休假地点", d.getLeaveLocation(), "天数", d.getLeaveDays() == null ? null : d.getLeaveDays().toPlainString(),
                "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private LocalDateTime parseDateTime(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return DateUtil.parseLocalDateTime(s); } catch (Exception e) { return null; }
    }
}
