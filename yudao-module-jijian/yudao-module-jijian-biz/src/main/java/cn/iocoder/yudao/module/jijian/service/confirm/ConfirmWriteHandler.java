package cn.iocoder.yudao.module.jijian.service.confirm;

import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import java.util.List;
import java.util.Map;

/**
 * 确认写入业务表处理器接口
 * 每种业务类型实现一个 Handler，由 ConfirmWriteHandlerRegistry 统一注册和路由。
 */
public interface ConfirmWriteHandler {

    /** 返回本 Handler 负责的 formType 字符串，需与 FormTypeConstants 保持一致 */
    String getFormType();

    /**
     * 返回本 Handler 写入的正式业务表名（用于 parsedData 追溯回写）。
     * 例如："jijian_property"、"jijian_canteen_supplier"
     * <p>新增 Handler 时必须实现此方法。
     */
    String getBusinessTableName();

    /**
     * 执行确认写入：从解析记录提取字段、校验、事务内写入正式业务表。
     * @param parsedData 解析数据 DO（含 parsedJson 和 correctedJson）
     * @return 写入结果（含创建的业务记录 ID 列表和 businessTable）
     */
    ConfirmWriteResult doConfirm(ParsedDataDO parsedData);

    /**
     * 根据来源解析数据ID查询已写入的业务记录摘要（用于幂等回查）。
     * Key 为字段显示名，Value 为字段值字符串；其中"记录ID"键必须包含。
     */
    List<Map<String, String>> queryConfirmedSummary(Long sourceParsedDataId);

}
