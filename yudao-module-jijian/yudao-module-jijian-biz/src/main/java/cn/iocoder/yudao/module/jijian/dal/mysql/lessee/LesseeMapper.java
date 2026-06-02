package cn.iocoder.yudao.module.jijian.dal.mysql.lessee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.dal.dataobject.lessee.LesseeDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface LesseeMapper extends BaseMapperX<LesseeDO> {

    default List<LesseeDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LesseeDO>()
                .eq(LesseeDO::getSourceParsedDataId, sourceParsedDataId));
    }

}
