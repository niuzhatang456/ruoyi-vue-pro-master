package cn.iocoder.yudao.module.jijian.dal.mysql.leavepersonal;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavepersonal.LeavePersonalDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeavePersonalMapper extends BaseMapperX<LeavePersonalDO> {

    default List<LeavePersonalDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeavePersonalDO>()
                .eq(LeavePersonalDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
