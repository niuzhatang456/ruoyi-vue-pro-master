package cn.iocoder.yudao.module.jijian.dal.mysql.leavehealth;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.leavehealth.LeaveHealthDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LeaveHealthMapper extends BaseMapperX<LeaveHealthDO> {

    default List<LeaveHealthDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LeaveHealthDO>()
                .eq(LeaveHealthDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
