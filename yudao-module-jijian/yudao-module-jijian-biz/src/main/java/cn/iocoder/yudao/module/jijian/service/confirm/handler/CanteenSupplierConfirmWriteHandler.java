package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier.CanteenSupplierMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class CanteenSupplierConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private CanteenSupplierMapper canteenSupplierMapper;

    @Override public String getFormType() { return FormTypeConstants.CANTEEN; }

    @Override
    public String getBusinessTableName() {{ return "jijian_canteen_supplier"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        List<Long> ids = new ArrayList<>();
        List<String> skippedMessages = new ArrayList<>();
        List<String> failedMessages = new ArrayList<>();
        int skippedCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            int rowNum = i + 1;
            // 物品名称：支持多种列名；空白行（小计/总计/签字行）直接跳过
            String itemName = get(row, "物品名称", "项目名称", "品名", "商品名称", "食材名称", "名称");
            if (StrUtil.isBlank(itemName) || isSummaryRow(itemName)) {
                skippedCount++;
                if (skippedMessages.size() < 20) skippedMessages.add("第 " + rowNum + " 行：空白行或汇总行，已跳过");
                continue;
            }

            // 单价
            String priceStr = get(row, "单价", "价格", "含税单价");
            BigDecimal price = parseBigDecimal(priceStr);
            // 数量
            BigDecimal quantity = parseBigDecimal(get(row, "数量", "件数", "重量"));
            // 小计
            BigDecimal subtotal = parseBigDecimal(get(row, "小计", "金额", "合计金额"));
            // 规格等级
            String specLevel = getWithNormalize(row, "规格等级", "规格、等级", "规格,等级", "规格", "等级");
            // 采购点（不含"供应商地点"，避免模糊匹配到"供应商"列）
            String purchasePoint = get(row, "采价点", "采购点", "采购地点", "采价地点", "供应点", "购买地点", "进货点");
            // 供应商名称（由解析阶段注入或用户在预览中补填；精确列名匹配，
            // 避免"供货单位/配送单位"等别名与"单位"列、"供应商地点"列互相模糊误配）
            String supplierName = getExact(row, "供应商", "供货商", "供货单位", "配送单位", "供应单位");
            // 配送日期（由解析阶段注入）
            LocalDate supplyDate = parseLocalDate(get(row, "时间", "日期", "配送日期", "供应日期"));
            if (existsSameBusinessData(itemName, specLevel, get(row, "单位", "计量单位"), quantity, price,
                    subtotal, supplierName, supplyDate)) {
                skippedCount++;
                if (skippedMessages.size() < 20) skippedMessages.add("第 " + rowNum + " 行：数据库已存在相同食堂供应数据，已跳过");
                continue;
            }

            CanteenSupplierDO do_ = CanteenSupplierDO.builder()
                    .itemName(itemName)
                    .specLevel(specLevel)
                    .unit(get(row, "单位", "计量单位"))
                    .quantity(quantity)
                    .price(price)
                    .subtotal(subtotal)
                    .supplierName(supplierName)
                    .supplyDate(supplyDate)
                    .purchasePoint(purchasePoint)
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            canteenSupplierMapper.insert(do_);
            ids.add(do_.getId());
        }
        if (ids.isEmpty() && skippedCount == 0) throw exception(PARSED_DATA_ROWS_EMPTY);
        return ConfirmWriteResult.ofWithStats(getFormType(), getBusinessTableName(), ids,
                rows.size(), skippedCount, skippedMessages, failedMessages.size(), failedMessages);
    }

    private boolean isSummaryRow(String value) {
        return value.contains("合计") || value.contains("小计") || value.contains("汇总");
    }

    private boolean existsSameBusinessData(String itemName, String specLevel, String unit, BigDecimal quantity,
                                           BigDecimal price, BigDecimal subtotal, String supplierName,
                                           LocalDate supplyDate) {
        LambdaQueryWrapper<CanteenSupplierDO> wrapper = new LambdaQueryWrapper<CanteenSupplierDO>()
                .eq(CanteenSupplierDO::getItemName, itemName)
                .eq(StrUtil.isNotBlank(specLevel), CanteenSupplierDO::getSpecLevel, specLevel)
                .eq(StrUtil.isNotBlank(unit), CanteenSupplierDO::getUnit, unit)
                .eq(quantity != null, CanteenSupplierDO::getQuantity, quantity)
                .eq(price != null, CanteenSupplierDO::getPrice, price)
                .eq(subtotal != null, CanteenSupplierDO::getSubtotal, subtotal)
                .eq(StrUtil.isNotBlank(supplierName), CanteenSupplierDO::getSupplierName, supplierName)
                .eq(supplyDate != null, CanteenSupplierDO::getSupplyDate, supplyDate);
        return canteenSupplierMapper.selectCount(wrapper) > 0;
    }

    /** 精确列名匹配（trim 后 equals），不做子串模糊匹配 */
    private String getExact(Map<String, String> row, String... aliases) {
        for (String alias : aliases) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (alias.equals(StrUtil.trim(entry.getKey()))) {
                    String v = StrUtil.trim(entry.getValue());
                    if (StrUtil.isNotBlank(v)) return v;
                }
            }
        }
        return null;
    }

    private BigDecimal parseBigDecimal(String s) {
        if (StrUtil.isBlank(s)) return null;
        try { return new BigDecimal(s.replaceAll("[^0-9.]", "")); } catch (Exception ignored) { return null; }
    }

    private LocalDate parseLocalDate(String s) {
        if (StrUtil.isBlank(s)) return null;
        // 支持 yyyy-MM-dd、yyyy/M/d，以及用户手工补填的 yyyy年M月d日/号
        String t = s.trim().replace("年", "-").replace("月", "-").replace("日", "").replace("号", "");
        String[] patterns = {"yyyy-MM-dd", "yyyy/MM/dd", "yyyy/M/d", "yyyy-M-d"};
        for (String pattern : patterns) {
            try { return LocalDate.parse(t, DateTimeFormatter.ofPattern(pattern)); } catch (Exception ignored) {}
        }
        return null;
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<CanteenSupplierDO> list = canteenSupplierMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (CanteenSupplierDO d : list) {
            result.add(toSummaryMap(
                "物品名称", d.getItemName(),
                "规格等级", d.getSpecLevel(),
                "数量", d.getQuantity() == null ? null : d.getQuantity().toPlainString(),
                "单价", d.getPrice() == null ? null : d.getPrice().toPlainString(),
                "小计", d.getSubtotal() == null ? null : d.getSubtotal().toPlainString(),
                "供应商", d.getSupplierName(),
                "时间", d.getSupplyDate() == null ? null : d.getSupplyDate().toString(),
                "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }
}
