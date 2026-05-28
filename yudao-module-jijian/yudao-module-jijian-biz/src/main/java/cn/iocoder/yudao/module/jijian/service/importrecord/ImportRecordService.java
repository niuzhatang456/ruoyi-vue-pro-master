package cn.iocoder.yudao.module.jijian.service.importrecord;

import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;

import java.util.List;

public interface ImportRecordService {

    ImportRecordDO createImportRecord(String fileName, String sourceType);

    List<ImportRecordDO> createImportRecords(List<String> fileNames, String sourceType);

    List<ImportRecordDO> getImportRecordList();

    ImportRecordDO getImportRecord(Long id);

}
