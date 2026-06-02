package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.Property.PropertyMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class PropertyConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private PropertyMapper propertyMapper;

    @Override
    public String getFormType() { return FormTypeConstants.PROPERTY; }

    @Override
    public String getBusinessTableName() {{ return "jijian_property"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);

        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String address = get(row, "房产地址", "地址");
            String name    = get(row, "房产名称", "名称");
            String owner   = get(row, "产权信息", "产权");
            if (StrUtil.isBlank(address) || StrUtil.isBlank(name) || StrUtil.isBlank(owner)) {
                throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            }
            String areaStr = get(row, "面积", "建筑面积");
            BigDecimal area = null;
            if (StrUtil.isNotBlank(areaStr)) {
                try { area = new BigDecimal(areaStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
            }
            PropertyDO do_ = PropertyDO.builder()
                    .propertyAddress(address).propertyName(name).ownershipInfo(owner)
                    .area(area)
                    .leaseStatus(get(row, "租赁情况", "出租状态", "出租情况"))
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            propertyMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<PropertyDO> list = propertyMapper.selectList(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PropertyDO>()
                .eq(PropertyDO::getSourceParsedDataId, sourceParsedDataId));
        List<Map<String, String>> result = new ArrayList<>();
        for (PropertyDO d : list) {
            result.add(toSummaryMap(
                "房产地址", d.getPropertyAddress(), "房产名称", d.getPropertyName(),
                "产权信息", d.getOwnershipInfo(),   "面积",     d.getArea() == null ? null : d.getArea().toPlainString() + " ㎡",
                "租赁情况", d.getLeaseStatus(),      "记录ID",   String.valueOf(d.getId())));
        }
        return result;
    }
}
