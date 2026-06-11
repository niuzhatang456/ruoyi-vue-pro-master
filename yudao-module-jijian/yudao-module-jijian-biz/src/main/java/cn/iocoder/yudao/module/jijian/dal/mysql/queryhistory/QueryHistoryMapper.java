package cn.iocoder.yudao.module.jijian.dal.mysql.queryhistory;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo.QueryHistoryPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory.QueryHistoryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QueryHistoryMapper extends BaseMapperX<QueryHistoryDO> {

    default PageResult<QueryHistoryDO> selectPage(QueryHistoryPageReqVO reqVO, Long userId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<QueryHistoryDO>()
                .eqIfPresent(QueryHistoryDO::getUserId, userId)
                .likeIfPresent(QueryHistoryDO::getQuestion, reqVO.getQuestion())
                .orderByDesc(QueryHistoryDO::getId));
    }

    default QueryHistoryDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<QueryHistoryDO>()
                .eq(QueryHistoryDO::getId, id)
                .eq(QueryHistoryDO::getUserId, userId));
    }
}
