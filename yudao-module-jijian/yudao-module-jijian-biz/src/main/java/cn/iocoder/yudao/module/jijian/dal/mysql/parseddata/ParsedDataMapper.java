package cn.iocoder.yudao.module.jijian.dal.mysql.parseddata;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.parseddata.ParsedDataDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

    /**
     * 原子 CAS：仅当 confirm_status='pending' 时将其置为 'confirmed'。
     * 返回受影响行数：1 表示成功抢占，0 表示已被其他请求确认（幂等路径）。
     */
    default int casConfirmStatus(Long id, String expectStatus, String newStatus) {
        return update(null, new LambdaUpdateWrapper<ParsedDataDO>()
                .set(ParsedDataDO::getConfirmStatus, newStatus)
                .eq(ParsedDataDO::getId, id)
                .eq(ParsedDataDO::getConfirmStatus, expectStatus));
    }

}
