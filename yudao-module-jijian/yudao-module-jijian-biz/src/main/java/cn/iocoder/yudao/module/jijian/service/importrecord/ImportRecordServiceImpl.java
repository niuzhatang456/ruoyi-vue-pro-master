package cn.iocoder.yudao.module.jijian.service.importrecord;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import cn.iocoder.yudao.module.jijian.dal.mysql.importrecord.ImportRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Validated
public class ImportRecordServiceImpl implements ImportRecordService {

    private static final String SOURCE_TYPE_OCR = "ocr";
    private static final String SOURCE_TYPE_EXCEL = "excel";
    private static final String SOURCE_TYPE_DRAG = "drag";

    @Resource
    private ImportRecordMapper importRecordMapper;

    @Override
    public ImportRecordDO createImportRecord(String fileName, String sourceType) {
        String normalizedSourceType = StrUtil.blankToDefault(sourceType, SOURCE_TYPE_OCR);
        ImportRecordDO record = ImportRecordDO.builder()
                .fileName(StrUtil.blankToDefault(fileName, "未命名文件"))
                .sourceType(normalizedSourceType)
                .detectedFormType("未知类型")
                .status("processing")
                .createdAt(LocalDateTime.now())
                .build();
        importRecordMapper.insert(record);
        return record;
    }

    @Override
    public List<ImportRecordDO> createImportRecords(List<String> fileNames, String sourceType) {
        return fileNames.stream()
                .map(fileName -> createImportRecord(fileName, sourceType))
                .collect(Collectors.toList());
    }

    @Override
    public List<ImportRecordDO> getImportRecordList() {
        return importRecordMapper.selectRecentList();
    }

    @Override
    public ImportRecordDO getImportRecord(Long id) {
        return importRecordMapper.selectById(id);
    }

}
