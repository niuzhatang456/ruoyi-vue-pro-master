package cn.iocoder.yudao.module.jijian.dal.mysql.attendancedaily;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.attendancedaily.AttendanceDailyDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface AttendanceDailyMapper extends BaseMapperX<AttendanceDailyDO> {

    default List<AttendanceDailyDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<AttendanceDailyDO>()
                .eq(AttendanceDailyDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
