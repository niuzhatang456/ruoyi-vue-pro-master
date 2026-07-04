package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.date.DateUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract.LeaseContractMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import cn.iocoder.yudao.module.jijian.service.parseddata.LeaseContractParseService;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class LeaseContractConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private LeaseContractMapper leaseContractMapper;

    @Override public String getFormType() { return FormTypeConstants.LEASE_CONTRACT; }

    @Override
    public String getBusinessTableName() {{ return "jijian_lease_contract"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        Set<String> dedupKeys = new LinkedHashSet<>();
        List<Long> ids = new ArrayList<>();
        List<String> skippedMessages = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();
        int skippedCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 1;
            LocalDate signDate = parseDate(get(row, "合同签订日期", "合同时间", "合同签订时间", "签订时间", "签订日期"));
            LocalDate leaseStartDate = parseDate(get(row, "租赁开始时间", "合同开始时间", "合同开始日期", "开始时间", "起租时间"));
            LocalDate leaseEndDate = parseDate(get(row, "租赁结束时间", "合同结束时间", "合同结束日期", "结束时间", "到期时间"));
            String lessorName = get(row, "出租方", "甲方");
            String lesseeName = get(row, "承租方", "乙方", "承租人", "租户");
            String waterFee = get(row, "水费");
            String electricityFee = get(row, "电费");
            String houseCondition = get(row, "房屋状况");
            String leasePurpose = get(row, "租赁用途");
            String leaseYears = get(row, "租赁年份", "租赁年限");
            String remark = get(row, "备注");
            LeaseContractDO do_ = LeaseContractDO.builder()
                    .contractNo(get(row, "合同编号", "编号", "合同号"))
                    .lessorName(lessorName)
                    .lesseeName(lesseeName)
                    .lesseeIdCard(get(row, "承租人身份证号", "身份证号"))
                    .lesseePhone(get(row, "承租人联系电话", "联系电话", "电话"))
                    .contractSignDate(signDate)
                    .leaseStartDate(leaseStartDate)
                    .leaseEndDate(leaseEndDate)
                    .leaseYears(leaseYears)
                    .houseCondition(houseCondition)
                    .leasePurpose(leasePurpose)
                    .rentInfoJson(buildRentInfoJson(parsedData, row))
                    .deposit(get(row, "保证金", "押金", "履约保证金"))
                    .waterFee(waterFee)
                    .electricityFee(electricityFee)
                    .remark(remark)
                    .originalFileName(metadata(parsedData, row,
                            LeaseContractParseService.META_ORIGINAL_FILE_NAME, "originalFileName"))
                    .originalFileUrl(metadata(parsedData, row,
                            LeaseContractParseService.META_ORIGINAL_FILE_URL, "originalFileUrl"))
                    .originalFilePath(metadata(parsedData, row,
                            LeaseContractParseService.META_ORIGINAL_FILE_PATH, "originalFilePath"))
                    .ocrRawText(metadata(parsedData, row,
                            LeaseContractParseService.META_OCR_RAW_TEXT, "ocrText"))
                    .parseStatus(StrUtil.blankToDefault(metadata(parsedData, row,
                            LeaseContractParseService.META_PARSE_STATUS, "parseStatus"), "success"))
                    .parseErrorMsg(metadata(parsedData, row,
                            LeaseContractParseService.META_PARSE_ERROR_MSG, "parseErrorMsg"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            if (!dedupKeys.add(dedupKey(do_))) {
                skippedCount++;
                if (skippedMessages.size() < 20) {
                    skippedMessages.add("第 " + rowNum + " 行：数据库已存在相同租赁合同数据，已跳过");
                }
                continue;
            }
            List<LeaseContractDO> sameBusinessData = findSameBusinessData(do_);
            if (!sameBusinessData.isEmpty()) {
                LeaseContractDO target = sameBusinessData.get(0);
                boolean changed = mergeRicherFields(target, do_);
                for (int j = 1; j < sameBusinessData.size(); j++) {
                    LeaseContractDO duplicate = sameBusinessData.get(j);
                    changed = mergeRicherFields(target, duplicate) || changed;
                    leaseContractMapper.deleteById(duplicate.getId());
                }
                if (changed) {
                    leaseContractMapper.updateById(target);
                }
                skippedCount++;
                if (skippedMessages.size() < 20) {
                    skippedMessages.add("绗?" + rowNum + " 琛岋細鏁版嵁搴撳凡瀛樺湪鐩稿悓绉熻祦鍚堝悓鏁版嵁锛屽凡璺宠繃");
                }
                continue;
            }
            leaseContractMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages, failedMessages.size(), failedMessages);
    }

    private String dedupKey(LeaseContractDO value) {
        return StrUtil.blankToDefault(value.getContractNo(), "") + "|"
                + StrUtil.blankToDefault(value.getLessorName(), "") + "|"
                + StrUtil.blankToDefault(value.getLesseeName(), "") + "|"
                + (value.getLeaseStartDate() == null ? "" : value.getLeaseStartDate()) + "|"
                + (value.getLeaseEndDate() == null ? "" : value.getLeaseEndDate());
    }

    private List<LeaseContractDO> findSameBusinessData(LeaseContractDO value) {
        if (StrUtil.isNotBlank(value.getContractNo())) {
            return leaseContractMapper.selectList(new LambdaQueryWrapper<LeaseContractDO>()
                    .eq(LeaseContractDO::getContractNo, value.getContractNo())
                    .orderByAsc(LeaseContractDO::getId));
        }
        LambdaQueryWrapper<LeaseContractDO> wrapper = new LambdaQueryWrapper<LeaseContractDO>()
                .eq(StrUtil.isNotBlank(value.getLessorName()), LeaseContractDO::getLessorName, value.getLessorName())
                .eq(StrUtil.isNotBlank(value.getLesseeName()), LeaseContractDO::getLesseeName, value.getLesseeName())
                .eq(value.getLeaseStartDate() != null, LeaseContractDO::getLeaseStartDate, value.getLeaseStartDate())
                .eq(value.getLeaseEndDate() != null, LeaseContractDO::getLeaseEndDate, value.getLeaseEndDate());
        return leaseContractMapper.selectList(wrapper);
    }

    private boolean mergeRicherFields(LeaseContractDO target, LeaseContractDO source) {
        boolean changed = false;
        if (StrUtil.isBlank(target.getContractNo()) && StrUtil.isNotBlank(source.getContractNo())) { target.setContractNo(source.getContractNo()); changed = true; }
        if (StrUtil.isBlank(target.getLessorName()) && StrUtil.isNotBlank(source.getLessorName())) { target.setLessorName(source.getLessorName()); changed = true; }
        if (StrUtil.isBlank(target.getLesseeName()) && StrUtil.isNotBlank(source.getLesseeName())) { target.setLesseeName(source.getLesseeName()); changed = true; }
        if (StrUtil.isBlank(target.getLesseeIdCard()) && StrUtil.isNotBlank(source.getLesseeIdCard())) { target.setLesseeIdCard(source.getLesseeIdCard()); changed = true; }
        if (StrUtil.isBlank(target.getLesseePhone()) && StrUtil.isNotBlank(source.getLesseePhone())) { target.setLesseePhone(source.getLesseePhone()); changed = true; }
        if (target.getContractSignDate() == null && source.getContractSignDate() != null) { target.setContractSignDate(source.getContractSignDate()); changed = true; }
        if (target.getLeaseStartDate() == null && source.getLeaseStartDate() != null) { target.setLeaseStartDate(source.getLeaseStartDate()); changed = true; }
        if (target.getLeaseEndDate() == null && source.getLeaseEndDate() != null) { target.setLeaseEndDate(source.getLeaseEndDate()); changed = true; }
        if (StrUtil.isBlank(target.getLeaseYears()) && StrUtil.isNotBlank(source.getLeaseYears())) { target.setLeaseYears(source.getLeaseYears()); changed = true; }
        if (StrUtil.isBlank(target.getHouseCondition()) && StrUtil.isNotBlank(source.getHouseCondition())) { target.setHouseCondition(source.getHouseCondition()); changed = true; }
        if (StrUtil.isBlank(target.getLeasePurpose()) && StrUtil.isNotBlank(source.getLeasePurpose())) { target.setLeasePurpose(source.getLeasePurpose()); changed = true; }
        if (StrUtil.isBlank(target.getDeposit()) && StrUtil.isNotBlank(source.getDeposit())) { target.setDeposit(source.getDeposit()); changed = true; }
        if (shouldReplaceFee(target.getWaterFee(), source.getWaterFee())) { target.setWaterFee(source.getWaterFee()); changed = true; }
        if (shouldReplaceFee(target.getElectricityFee(), source.getElectricityFee())) { target.setElectricityFee(source.getElectricityFee()); changed = true; }
        if (StrUtil.isBlank(target.getRemark()) && StrUtil.isNotBlank(source.getRemark())) { target.setRemark(source.getRemark()); changed = true; }
        if (StrUtil.isBlank(target.getOriginalFileName()) && StrUtil.isNotBlank(source.getOriginalFileName())) { target.setOriginalFileName(source.getOriginalFileName()); changed = true; }
        if (StrUtil.isBlank(target.getOriginalFileUrl()) && StrUtil.isNotBlank(source.getOriginalFileUrl())) { target.setOriginalFileUrl(source.getOriginalFileUrl()); changed = true; }
        if (StrUtil.isBlank(target.getOriginalFilePath()) && StrUtil.isNotBlank(source.getOriginalFilePath())) { target.setOriginalFilePath(source.getOriginalFilePath()); changed = true; }
        if (StrUtil.isBlank(target.getParseStatus()) && StrUtil.isNotBlank(source.getParseStatus())) { target.setParseStatus(source.getParseStatus()); changed = true; }
        if (StrUtil.isBlank(target.getParseErrorMsg()) && StrUtil.isNotBlank(source.getParseErrorMsg())) { target.setParseErrorMsg(source.getParseErrorMsg()); changed = true; }
        if (isRicherRentInfo(target.getRentInfoJson(), source.getRentInfoJson())) {
            target.setRentInfoJson(source.getRentInfoJson());
            changed = true;
        }
        if (StrUtil.length(source.getOcrRawText()) > StrUtil.length(target.getOcrRawText())) {
            target.setOcrRawText(source.getOcrRawText());
            changed = true;
        }
        return changed;
    }

    private boolean shouldReplaceFee(String target, String source) {
        if (StrUtil.isBlank(source)) {
            return false;
        }
        if (StrUtil.isBlank(target)) {
            return true;
        }
        boolean sourceSpecific = source.contains("元") && (source.contains("/") || source.contains("吨")
                || source.contains("度") || source.contains("月") || source.contains("年"));
        if (!sourceSpecific) {
            return false;
        }
        return target.contains("物业管理费") || target.contains("收取") || target.length() > source.length() + 3;
    }

    private boolean isRicherRentInfo(String targetRentInfoJson, String sourceRentInfoJson) {
        int sourceCount = rentItemCount(sourceRentInfoJson);
        int targetCount = rentItemCount(targetRentInfoJson);
        if (sourceCount == 0) {
            return false;
        }
        if (sourceCount > targetCount) {
            return true;
        }
        if (sourceCount < targetCount) {
            return false;
        }
        BigDecimal sourceMax = maxRentAmount(sourceRentInfoJson);
        BigDecimal targetMax = maxRentAmount(targetRentInfoJson);
        return sourceMax.compareTo(BigDecimal.ZERO) > 0
                && sourceMax.compareTo(targetMax) > 0
                && (targetMax.compareTo(new BigDecimal("100")) < 0
                || sourceMax.compareTo(targetMax.multiply(new BigDecimal("2"))) > 0);
    }

    private int rentItemCount(String rentInfoJson) {
        if (StrUtil.isBlank(rentInfoJson)) {
            return 0;
        }
        try {
            return JSONUtil.parseArray(rentInfoJson).size();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private BigDecimal maxRentAmount(String rentInfoJson) {
        if (StrUtil.isBlank(rentInfoJson)) {
            return BigDecimal.ZERO;
        }
        try {
            JSONArray array = JSONUtil.parseArray(rentInfoJson);
            BigDecimal max = BigDecimal.ZERO;
            for (int i = 0; i < array.size(); i++) {
                String raw = array.getJSONObject(i).getStr("rentAmount", "");
                String numeric = raw.replace("，", ",").replace(",", "").replaceAll("[^0-9.]", "");
                if (StrUtil.isBlank(numeric)) {
                    continue;
                }
                BigDecimal amount = new BigDecimal(numeric);
                if (amount.compareTo(max) > 0) {
                    max = amount;
                }
            }
            return max;
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LeaseContractDO> list = leaseContractMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LeaseContractDO d : list) {
            result.add(toSummaryMap("合同编号", d.getContractNo(), "出租方", d.getLessorName(),
                    "承租方", d.getLesseeName(), "租赁开始时间", d.getLeaseStartDate() == null ? null : d.getLeaseStartDate().toString(),
                    "租赁结束时间", d.getLeaseEndDate() == null ? null : d.getLeaseEndDate().toString(),
                    "租赁年份", d.getLeaseYears(), "租金信息", d.getRentInfoJson(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private String buildRentInfoJson(Map<String, String> row) {
        return buildRentInfoJson(null, row);
    }

    private String buildRentInfoJson(ParsedDataDO parsedData, Map<String, String> row) {
        String structured = firstStructuredRentJson(parsedData, row);
        if (StrUtil.isNotBlank(structured)) {
            return structured;
        }
        JSONArray array = new JSONArray();
        int maxIndex = findMaxRentIndex(row);
        for (int i = 1; i <= maxIndex; i++) {
            String rentAmount = cell(row, "房屋租金" + i);
            String paymentDate = cell(row, "租金交纳日期" + i);
            if (StrUtil.isBlank(rentAmount) && StrUtil.isBlank(paymentDate)) {
                continue;
            }
            JSONObject item = new JSONObject(true);
            item.set("index", i);
            item.set("rentAmount", StrUtil.blankToDefault(rentAmount, ""));
            item.set("paymentDate", StrUtil.blankToDefault(paymentDate, ""));
            String year = extractYear(rentAmount, paymentDate);
            if (StrUtil.isNotBlank(year)) {
                item.set("year", year);
            }
            array.add(item);
        }
        return JSONUtil.toJsonStr(array);
    }

    private String firstStructuredRentJson(ParsedDataDO parsedData, Map<String, String> row) {
        for (String candidate : new String[] {
                cell(row, "租金明细JSON"),
                cell(row, "rentInfoJson"),
                parsedData == null ? "" : metadata(parsedData, row, "租金明细JSON", "rentInfoJson")
        }) {
            if (StrUtil.isBlank(candidate)) {
                continue;
            }
            try {
                JSONArray source = JSONUtil.parseArray(candidate);
                JSONArray target = new JSONArray();
                for (int i = 0; i < source.size(); i++) {
                    JSONObject src = source.getJSONObject(i);
                    JSONObject item = new JSONObject(true);
                    item.set("index", src.getInt("index", i + 1));
                    item.set("year", StrUtil.blankToDefault(src.getStr("year"), ""));
                    item.set("paymentDate", StrUtil.blankToDefault(src.getStr("paymentDate"), src.getStr("paymentText", "")));
                    item.set("rentAmount", StrUtil.blankToDefault(src.getStr("rentAmount"), src.getStr("rentText", "")));
                    target.add(item);
                }
                return JSONUtil.toJsonStr(target);
            } catch (Exception ignored) {
                // 非 JSON 时继续回退旧字段
            }
        }
        return "";
    }

    private String extractYear(String rentAmount, String paymentDate) {
        Matcher matcher = Pattern.compile("(20\\d{2})").matcher(StrUtil.blankToDefault(rentAmount, "") + " " + StrUtil.blankToDefault(paymentDate, ""));
        return matcher.find() ? matcher.group(1) : "";
    }

    private int findMaxRentIndex(Map<String, String> row) {
        Pattern pattern = Pattern.compile("^(?:房屋租金|租金交纳日期)(\\d+)$");
        int max = 0;
        for (String key : row.keySet()) {
            Matcher matcher = pattern.matcher(key);
            if (matcher.find()) {
                try { max = Math.max(max, Integer.parseInt(matcher.group(1))); } catch (Exception ignored) {}
            }
        }
        return Math.max(max, 5);
    }

    private String cell(Map<String, String> row, String key) {
        String value = row.get(key);
        return StrUtil.isBlank(value) ? "" : StrUtil.trim(value);
    }

    private String metadata(ParsedDataDO parsedData, Map<String, String> row, String rowKey, String rootKey) {
        String value = cell(row, rowKey);
        if (StrUtil.isNotBlank(value)) {
            return value;
        }
        try {
            JSONObject root = JSONUtil.parseObj(getActiveJson(parsedData));
            return root.getStr(rootKey, "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private LocalDate parseDate(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            LocalDateTime dateTime = parseDateTime(value);
            return dateTime == null ? null : dateTime.toLocalDate();
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return DateUtil.parseLocalDateTime(value);
        } catch (Exception ignored) {
            try {
                return DateUtil.parse(value).toLocalDateTime();
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }
}
