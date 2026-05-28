package cn.iocoder.yudao.module.jijian.dal.mysql.parseddata;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ParsedDataMapper extends BaseMapperX<ParsedDataDO> {

    default ParsedDataDO selectLatestByImportRecordId(Long importRecordId) {
        return selectOne(new LambdaQueryWrapperX<ParsedDataDO>()
                .eq(ParsedDataDO::getImportRecordId, importRecordId)
                .orderByDesc(ParsedDataDO::getId)
                .last("LIMIT 1"));
    }

    default List<ParsedDataDO> selectListByImportRecordId(Long importRecordId) {
        return selectList(new LambdaQueryWrapperX<ParsedDataDO>()
                .eqIfPresent(ParsedDataDO::getImportRecordId, importRecordId)
                .orderByDesc(ParsedDataDO::getId));
    }

}
