package cn.iocoder.yudao.module.jijian.dal.mysql.importrecord;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.importrecord.ImportRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ImportRecordMapper extends BaseMapperX<ImportRecordDO> {

    default List<ImportRecordDO> selectRecentList() {
        return selectList(new LambdaQueryWrapperX<ImportRecordDO>()
                .orderByDesc(ImportRecordDO::getCreatedAt)
                .orderByDesc(ImportRecordDO::getId)
                .last("LIMIT 50"));
    }

}
