package cn.iocoder.yudao.module.jijian.dal.mysql.lessee;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.dal.dataobject.lessee.LesseeDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface LesseeMapper extends BaseMapperX<LesseeDO> {

    default List<LesseeDO> selectListBySourceParsedDataId(Long sourceParsedDataId) {
        return selectList(new LambdaQueryWrapperX<LesseeDO>()
                .eq(LesseeDO::getSourceParsedDataId, sourceParsedDataId));
    }

    default List<LesseeDO> selectListForSummary(LocalDateTime startTime) {
        return selectList(new LambdaQueryWrapper<LesseeDO>()
                .ge(LesseeDO::getCreateTime, startTime));
    }

    default PageResult<LesseeDO> selectPageForQuery(PageParam pageParam, LocalDateTime startTime) {
        return selectPage(pageParam, new LambdaQueryWrapper<LesseeDO>()
                .ge(LesseeDO::getCreateTime, startTime)
                .orderByDesc(LesseeDO::getCreateTime));
    }

}
