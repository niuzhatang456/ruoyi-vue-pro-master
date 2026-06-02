package cn.iocoder.yudao.module.jijian.service.confirm.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.lessee.LesseeDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.lessee.LesseeMapper;
import cn.iocoder.yudao.module.jijian.enums.FormTypeConstants;
import cn.iocoder.yudao.module.jijian.service.confirm.AbstractConfirmWriteHandler;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.jijian.enums.ErrorCodeConstants.*;

@Component
public class LesseeConfirmWriteHandler extends AbstractConfirmWriteHandler {

    @Resource private LesseeMapper lesseeMapper;

    @Override public String getFormType() { return FormTypeConstants.LESSEE; }

    @Override
    public String getBusinessTableName() {{ return "jijian_lessee"; }}

    @Override
    public ConfirmWriteResult doConfirm(ParsedDataDO parsedData) {
        List<Map<String, String>> rows = extractAllRows(parsedData);
        if (rows.isEmpty()) throw exception(PARSED_DATA_ROWS_EMPTY);
        List<Long> ids = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = get(row, "联系人", "姓名");
            if (StrUtil.isBlank(name)) throw exception(PARSED_DATA_REQUIRED_FIELD_MISSING);
            String internalStr = get(row, "是否内部人员", "内部人员");
            LesseeDO do_ = LesseeDO.builder()
                    .entityType(get(row, "类型", "个人/组织", "主体类型"))
                    .contactName(name)
                    .phone(get(row, "手机号", "电话", "联系电话"))
                    .idCard(get(row, "身份证", "身份证号"))
                    .businessLicense(get(row, "营业执照", "营业执照号"))
                    .isInternalStaff("是".equals(internalStr) || "true".equalsIgnoreCase(internalStr))
                    .remark(get(row, "备注"))
                    .sourceParsedDataId(parsedData.getId())
                    .build();
            lesseeMapper.insert(do_);
            ids.add(do_.getId());
        }
        return ConfirmWriteResult.of(getFormType(), getBusinessTableName(), ids);
    }

    @Override
    public List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId) {
        List<LesseeDO> list = lesseeMapper.selectListBySourceParsedDataId(sourceParsedDataId);
        List<Map<String, String>> result = new ArrayList<>();
        for (LesseeDO d : list) {
            result.add(toSummaryMap("联系人", d.getContactName(), "手机号", d.getPhone(),
                "主体类型", d.getEntityType(), "身份证", d.getIdCard(), "记录ID", String.valueOf(d.getId())));
        }
        return result;
    }
}
