package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave.CompensatoryLeaveMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class CompensatoryLeaveConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private CompensatoryLeaveMapper compensatoryLeaveMapper;

    @Override public String getFormType() { return FormTypeConstants.COMPENSATORY; }

    @Override
    public String getBusinessTableName() {{ return "jijian_compensatory_leave"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = get(row, "申请人显示名", "申请人", "姓名");
            if (StrUtil.isBlank(name)) throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            String outsideStr = get(row, "是否出义", "出义");
            CompensatoryLeaveDO do_ = CompensatoryLeaveDO.builder()
                    .applicantName(name)
                    .employeeNo(get(row, "员工编号", "工号"))
                    .department(get(row, "部门"))
                    .overtimeStartTime(parseDateTime(get(row, "加班开始时间", "加班开始")))
                    .overtimeStartShift(get(row, "加班开始班次", "开始班次"))
                    .overtimeEndTime(parseDateTime(get(row, "加班结束时间", "加班结束")))
                    .overtimeEndShift(get(row, "加班结束班次", "结束班次"))
                    .compensatoryStartTime(parseDateTime(get(row, "调休开始时间", "调休开始")))
                    .compensatoryStartShift(get(row, "调休开始班次"))
                    .compensatoryEndTime(parseDateTime(get(row, "调休结束时间", "调休结束")))
                    .compensatoryEndShift(get(row, "调休结束班次"))
                    .compensatoryDuration(get(row, "调休时长", "时长"))
                    .isOutside("是".equals(outsideStr) || "true".equalsIgnoreCase(outsideStr))
                    .outsideLocation(get(row, "出义具体地点", "出义地点"))
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            compensatoryLeaveMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<CompensatoryLeaveDO> list = compensatoryLeaveMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (CompensatoryLeaveDO d : list) {
            result.add(toSummaryMap("申请人", d.getApplicantName(), "部门", d.getDepartment(),
                "调休时长", d.getCompensatoryDuration(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private LocalDateTime parseDateTime(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return DateUtil.parseLocalDateTime(s); } catch (Exception e) { return null; }
    }
}
