package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier.CanteenSupplierDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.canteensupplier.CanteenSupplierMapper;
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
        for (Map<String, String> row : rows) {
            // 物品名称：支持多种列名
            String itemName = get(row, "物品名称", "项目名称", "品名", "商品名称", "食材名称", "名称");
            if (StrUtil.isBlank(itemName)) throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            // 价格：支持"价格""单价""含税单价"
            String priceStr = get(row, "单价", "价格", "含税单价");
            BigDecimal price = null;
            if (StrUtil.isNotBlank(priceStr)) {
                try { price = new BigDecimal(priceStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
            }
            // 规格等级：支持"规格、等级""规格等级""规格""等级"
            String specLevel = getWithNormalize(row, "规格等级", "规格、等级", "规格,等级", "规格", "等级");
            // 采购点：支持采价点/采购地点/采价地点/供应点等全部别名
            String purchasePoint = get(row,
                    "采价点", "采购点", "采购地点", "采价地点", "供应点", "供应商地点", "购买地点", "进货点");
            CanteenSupplierDO do_ = CanteenSupplierDO.builder()
                    .itemName(itemName)
                    .specLevel(specLevel)
                    .unit(get(row, "单位", "计量单位"))
                    .price(price)
                    .purchasePoint(purchasePoint)
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            canteenSupplierMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<CanteenSupplierDO> list = canteenSupplierMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (CanteenSupplierDO d : list) {
            result.add(toSummaryMap("物品名称", d.getItemName(), "规格等级", d.getSpecLevel(),
                "单价", d.getPrice() == null ? null : d.getPrice().toPlainString(),
                "采购点", d.getPurchasePoint(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }
}
