package cn.iocoder.yudao.module.jijian.service.parseddata;

import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ParsedDataService {

    ParsedDataDO parseAndCreate(ImportRecordDO importRecord, MultipartFile file);

    ParsedDataDO createFailedParsedData(ImportRecordDO importRecord, String errorMsg);

    ParsedDataDO getLatestParsedData(Long importRecordId);

    List<ParsedDataDO> getParsedDataList(Long importRecordId);

}
