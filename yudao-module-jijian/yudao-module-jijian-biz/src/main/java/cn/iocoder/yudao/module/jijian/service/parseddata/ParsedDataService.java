package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import cn.iocoder.yudao.module.jijian.service.confirm.ConfirmWriteResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ParsedDataService {

    ParsedDataDO parseAndCreate(ImportRecordDO importRecord, MultipartFile file);

    /**
     * 按强制 formType 解析并创建解析数据。
     * 用于 9 个独立表单录入接口：服务端硬绑定 formType，不依赖文件内容自动识别。
     */
    ParsedDataDO parseAndCreateWithFormType(ImportRecordDO importRecord, MultipartFile file, String forcedFormType);

    ParsedDataDO createFailedParsedData(ImportRecordDO importRecord, String errorMsg);

    ParsedDataDO getParsedDataById(Long parsedDataId);

    ParsedDataDO getLatestParsedData(Long importRecordId);

    List<ParsedDataDO> getParsedDataList(Long importRecordId);

    /**
     * 保存用户对解析结果的人工校正 JSON
     */
    void saveCorrectedJson(Long parsedDataId, String correctedJson);

    /**
     * 通用确认写入：按 formType 路由到对应 Handler，写入正式业务表。
     * 幂等：已 confirmed 状态则直接返回汇总。
     */
    ConfirmWriteResult confirmWrite(Long parsedDataId);

    /**
     * 向后兼容：确认写入房产表并返回房产 ID。
     * 内部委托给 confirmWrite，从结果中取第一个 ID。
     */
    Long confirmProperty(Long parsedDataId);

}
