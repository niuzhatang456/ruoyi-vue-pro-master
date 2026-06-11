package cn.iocoder.yudao.module.jijian.dal.mysql.disposalrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.disposalrecord.DisposalRecordDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DisposalRecordMapper extends BaseMapperX<DisposalRecordDO> {

    default PageResult<DisposalRecordDO> selectPage(DisposalRecordPageReqVO reqVO, Long userId) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DisposalRecordDO>()
                .eqIfPresent(DisposalRecordDO::getDisposalUserId, userId)
                .likeIfPresent(DisposalRecordDO::getQueryQuestion, reqVO.getQueryQuestion())
                .orderByDesc(DisposalRecordDO::getDisposalTime));
    }

    default DisposalRecordDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<DisposalRecordDO>()
                .eq(DisposalRecordDO::getId, id)
                .eq(DisposalRecordDO::getDisposalUserId, userId));
    }
}
