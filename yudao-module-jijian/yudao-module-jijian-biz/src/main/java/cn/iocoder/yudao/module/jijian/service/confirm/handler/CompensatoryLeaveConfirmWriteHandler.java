package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.jijian.util.JijianPersonNameUtils;
import cn.iocoder.yudao.module.jijian.dal.dataobject.compensatoryleave.CompensatoryLeaveDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.compensatoryleave.CompensatoryLeaveMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

/**
 * 调休记录 确认写入 Handler。
 *
 * <p>修复点：
 * <ul>
 *   <li>新增更多中文列名别名（适配不同 Excel 表头习惯）；</li>
 *   <li>逐行收集错误，不再首行异常即全量回滚；</li>
 *   <li>仅当 <strong>全部行</strong> 均失败才整体抛出异常；部分失败时写入成功行，并在返回信息中说明跳过原因；</li>
 *   <li>使用 {@code insertBatch} 分批写入（500 条/批），提升大批量性能；</li>
 *   <li>错误信息包含行号和具体字段，方便前端展示定位。</li>
 * </ul>
 */
@Slf4j
@Component
public class CompensatoryLeaveConfirmWriteHandler extends AbstractConfirmWriteHandler {

    private static final int BATCH_SIZE = 500;

    @Resource
    private CompensatoryLeaveMapper compensatoryLeaveMapper;

    @Override
    public String getFormType() {
        return FormTypeConstants.COMPENSATORY;
    }

    @Override
    public String getBusinessTableName() {
        return "jijian_compensatory_leave";
    }

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        log.info("[JijianRuntimeMarker] 2026-06-fix-duty-fuzzy-v4 CompensatoryLeaveConfirmWriteHandler loaded");
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) {
            throw exception(PARSED_DATA_ROWS_EMPTY);
        }

        List<CompensatoryLeaveDO> toInsert = new ArrayList<>(rows.size());
        // 失败行（有业务字段但姓名为空 / 解析异常），最多记录 20 条
        List<String> failedMessages = new ArrayList<>();
        // 跳过行（纯空白行 / 合计行），最多记录 20 条
        List<String> skippedMessages = new ArrayList<>();
        int skippedCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 1;
            try {
                // ── 必填字段：姓名 / 申请人（兼容多种表头） ──
                String rawName = get(row,
                        "申请人显示名", "申请人", "姓名", "员工姓名", "职工姓名",
                        "员工名称", "姓 名", "工作人员", "人员");
                // 拆分姓名与工号
                JijianPersonNameUtils.ParseResult personParsed = JijianPersonNameUtils.parse(rawName);
                String name = (personParsed != null && personParsed.name != null) ? personParsed.name : rawName;
                if (StrUtil.isBlank(name)) {
                    // 判断是否有业务字段：有则计入 failedRows，无则计入 skippedRows（空白/汇总行）
                    String businessVal = get(row,
                            "调休时长", "时长", "调休天数", "调休开始时间", "调休结束时间",
                            "加班开始时间", "加班结束时间", "调休开始", "调休结束");
                    if (StrUtil.isNotBlank(businessVal)) {
                        if (failedMessages.size() < 20) {
                            failedMessages.add("第 " + rowNum + " 行：姓名/申请人为空，但存在调休字段，疑似合并单元格未展开或原始数据缺失");
                        }
                        log.warn("[CompensatoryConfirmWrite] 第 {} 行：有业务字段但姓名为空，疑似合并单元格问题。行摘要：{}", rowNum, rowSummary(row));
                    } else {
                        skippedCount++;
                        if (skippedMessages.size() < 20) {
                            skippedMessages.add("第 " + rowNum + " 行：空白行或合计行，已跳过");
                        }
                        log.debug("[CompensatoryConfirmWrite] 第 {} 行：整行无有效字段，视为空白/汇总行跳过", rowNum);
                    }
                    continue;
                }

                // is_outside：按原文存储，不转 0/1
                String isOutside = StrUtil.trimToNull(get(row, "是否出义", "出义", "是否外出", "外出"));

                // employee_no：优先显式列，否则取从姓名拆出的编号
                String explicitNo = get(row, "员工编号", "工号", "职工编号", "员工号");
                String parsedNo   = (personParsed != null) ? personParsed.employeeNo : null;
                String employeeNo = StrUtil.isNotBlank(explicitNo) ? explicitNo : parsedNo;

                CompensatoryLeaveDO entity = CompensatoryLeaveDO.builder()
                        .applicantName(name)
                        .employeeNo(employeeNo)
                        .department(get(row, "部门", "所在部门", "科室", "部门名称"))
                        .overtimeStartTime(parseDateTime(get(row, "加班开始时间", "加班开始", "加班日期"), rowNum, "加班开始时间", failedMessages))
                        .overtimeStartShift(get(row, "加班开始班次", "开始班次", "加班班次"))
                        .overtimeEndTime(parseDateTime(get(row, "加班结束时间", "加班结束"), rowNum, "加班结束时间", failedMessages))
                        .overtimeEndShift(get(row, "加班结束班次", "结束班次"))
                        .compensatoryStartTime(parseDateTime(get(row, "调休开始时间", "调休开始", "休息开始时间", "休息开始"), rowNum, "调休开始时间", failedMessages))
                        .compensatoryStartShift(get(row, "调休开始班次", "调休班次"))
                        .compensatoryEndTime(parseDateTime(get(row, "调休结束时间", "调休结束", "休息结束时间", "休息结束"), rowNum, "调休结束时间", failedMessages))
                        .compensatoryEndShift(get(row, "调休结束班次"))
                        .compensatoryDuration(get(row, "调休时长", "时长", "调休天数", "天数", "时长(天)", "调休时长(天)"))
                        .isOutside(isOutside)          // 原文存储
                        .outsideLocation(get(row, "出义具体地址", "出义具体地点", "出义地点", "外出地点", "外出地址"))
                        .remark(get(row, "备注", "说明", "原因", "调休原因"))
                        .sourceParsedDataId(parsedData.getId())
                        .build();
                toInsert.add(entity);

            } catch (ServiceException se) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：" + se.getMessage());
                log.warn("[CompensatoryConfirmWrite] 第 {} 行业务异常：{}", rowNum, se.getMessage());
            } catch (Exception e) {
                if (failedMessages.size() < 20) failedMessages.add("第 " + rowNum + " 行：解析失败 - " + e.getMessage());
                log.error("[CompensatoryConfirmWrite] 第 {} 行意外异常", rowNum, e);
            }
        }

        // 全部行均失败时抛出异常，告知前端详情
        if (toInsert.isEmpty()) {
            int shown = Math.min(failedMessages.size(), 5);
            String detail = failedMessages.isEmpty() ? "所有行均为空行或汇总行"
                    : String.join("；", failedMessages.subList(0, shown))
                    + (failedMessages.size() > 5 ? "……（共 " + failedMessages.size() + " 处错误）" : "");
            throw new ServiceException(PARSED_DATA_REQUIRED_FIELD_MISSING.getCode(),
                    "调休表全部行写入失败：" + detail);
        }

        // 在外层 @Transactional 事务内逐行写入（JDBC URL 含 rewriteBatchedStatements=true，驱动层自动合并批量）
        for (CompensatoryLeaveDO entity : toInsert) {
            compensatoryLeaveMapper.insert(entity);
        }

        List<Long> ids = new ArrayList<>(toInsert.size());
        for (CompensatoryLeaveDO entity : toInsert) {
            if (entity.getId() != null) ids.add(entity.getId());
        }

        log.info("[CompensatoryConfirmWrite] 写入完成：总行={} 成功={} 跳过={} 失败={}",
                rows.size(), ids.size(), skippedCount, failedMessages.size());

        return ConfirmWriteResult.ofWithStats(
                getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages,
                failedMessages.size(), failedMessages);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<CompensatoryLeaveDO> list = compensatoryLeaveMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (CompensatoryLeaveDO d : list) {
            result.add(toSummaryMap(
                    "申请人", d.getApplicantName(),
                    "部门", d.getDepartment(),
                    "调休时长", d.getCompensatoryDuration(),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    // ──────────────────────────────────────────────────────────────────────
    // 私有工具方法
    // ──────────────────────────────────────────────────────────────────────

    /**
     * 解析日期时间字符串；失败时记录警告（不加入 rowErrors，时间字段非强制）。
     */
    private LocalDateTime parseDateTime(String s, int rowNum, String fieldName, List<String> rowErrors) {
        if (StrUtil.isBlank(s)) {
            return null;
        }
        try {
            return DateUtil.parseLocalDateTime(s);
        } catch (Exception e1) {
            // 兼容纯日期字符串（如 "2026-02-18"）：Hutool parseLocalDateTime 可能不支持无时间部分的写法
            try {
                return DateUtil.parse(s).toLocalDateTime();
            } catch (Exception e2) {
                log.warn("[CompensatoryConfirmWrite] 第 {} 行字段「{}」日期格式无法识别，原始值=「{}」，将存为 null",
                        rowNum, fieldName, s);
                return null;
            }
        }
    }

    /** 取行内前两个非空字段的摘要，用于日志（不含敏感字段原文）。 */
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
