package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteenmarketprice.CanteenMarketPriceDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.canteenmarketprice.CanteenMarketPriceMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.PARSED_DATA_ROWS_EMPTY;

@Component
public class CanteenMarketPriceConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource
    private CanteenMarketPriceMapper canteenMarketPriceMapper;

    @Override
    public String getFormType() {
        return FormTypeConstants.CANTEEN_MARKET_PRICE;
    }

    @Override
    public String getBusinessTableName() {
        return "jijian_canteen_market_price";
    }

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) {
            throw exception(PARSED_DATA_ROWS_EMPTY);
        }

        JSONObject root = JSONUtil.parseObj(getActiveJson(parsedData));
        String sourceTitle = root.getStr("sourceTitle", "");
        String sourceFileName = root.getStr("fileName", "");
        String defaultMonth = normalizeMonth(root.getStr("priceMonth", ""));

        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String itemName = get(row, "项目名称", "商品名称", "品名", "名称");
            if (StrUtil.isBlank(itemName) || isSummaryRow(itemName)) {
                continue;
            }
            BigDecimal price = parseBigDecimal(get(row, "价格", "单价"));
            CanteenMarketPriceDO item = CanteenMarketPriceDO.builder()
                    .itemName(itemName)
                    .specLevel(getWithNormalize(row, "规格等级", "规格/等级", "规格、等级", "规格", "等级"))
                    .unit(get(row, "单位", "计量单位"))
                    .price(price)
                    .pricePoint(get(row, "采价点", "采价地点", "采价单位", "价格采集点"))
                    .priceMonth(StrUtil.blankToDefault(normalizeMonth(get(row, "日期", "月份", "公告年月")), defaultMonth))
                    .sourceTitle(sourceTitle)
                    .sourceFileName(sourceFileName)
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            canteenMarketPriceMapper.insert(item);
            ids.add(item.getId());
        }
        if (ids.isEmpty()) {
            throw exception(PARSED_DATA_ROWS_EMPTY);
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<CanteenMarketPriceDO> list = canteenMarketPriceMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (CanteenMarketPriceDO d : list) {
            result.add(toSummaryMap(
                    "项目名称", d.getItemName(),
                    "规格/等级", d.getSpecLevel(),
                    "单位", d.getUnit(),
                    "价格", d.getPrice() == null ? null : d.getPrice().toPlainString(),
                    "采价点", d.getPricePoint(),
                    "日期", d.getPriceMonth(),
                    "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }

    private boolean isSummaryRow(String value) {
        return value.contains("合计") || value.contains("小计") || value.contains("汇总");
    }

    private BigDecimal parseBigDecimal(String s) {
        if (StrUtil.isBlank(s)) return null;
        try {
            String normalized = s.replaceAll("[^0-9.]", "");
            return StrUtil.isBlank(normalized) ? null : new BigDecimal(normalized);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizeMonth(String value) {
        if (StrUtil.isBlank(value)) return "";
        String text = value.trim();
        java.util.regex.Matcher cn = java.util.regex.Pattern
                .compile("(20\\d{2})\\s*年\\s*(\\d{1,2})\\s*月").matcher(text);
        if (cn.find()) {
            return cn.group(1) + "-" + cn.group(2).replaceFirst("^(\\d)$", "0$1");
        }
        java.util.regex.Matcher dash = java.util.regex.Pattern
                .compile("(20\\d{2})[-/.](\\d{1,2})").matcher(text);
        if (dash.find()) {
            return dash.group(1) + "-" + dash.group(2).replaceFirst("^(\\d)$", "0$1");
        }
        return text;
    }
}
