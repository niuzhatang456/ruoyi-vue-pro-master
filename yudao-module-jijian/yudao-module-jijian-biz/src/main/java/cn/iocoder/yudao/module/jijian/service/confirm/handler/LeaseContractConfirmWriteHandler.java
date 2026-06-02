package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.iocoder.yudao.module.jijian.dal.dataobject.leasecontract.LeaseContractDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.leasecontract.LeaseContractMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String amountStr = get(row, "金额", "租金", "合同金额");
            BigDecimal amount = null;
            if (StrUtil.isNotBlank(amountStr)) {
                try { amount = new BigDecimal(amountStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
            }
            LeaseContractDO do_ = LeaseContractDO.builder()
                    .paymentStatus(get(row, "支付情况", "支付状态"))
                    .waterElectricityMgmt(get(row, "水电费管理", "水电"))
                    .contractSummary(get(row, "合同内容摘要", "合同摘要", "摘要"))
                    .amount(amount)
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            leaseContractMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LeaseContractDO> list = leaseContractMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LeaseContractDO d : list) {
            result.add(toSummaryMap("合同金额", d.getAmount() == null ? null : d.getAmount().toPlainString(),
                "支付情况", d.getPaymentStatus(), "合同摘要", d.getContractSummary(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }
}
