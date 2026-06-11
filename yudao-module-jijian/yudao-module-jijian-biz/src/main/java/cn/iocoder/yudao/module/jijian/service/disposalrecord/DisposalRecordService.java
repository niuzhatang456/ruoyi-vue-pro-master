package cn.iocoder.yudao.module.jijian.service.disposalrecord;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordCreateReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.disposalrecord.DisposalRecordDO;

public interface DisposalRecordService {

    Long createDisposalRecord(DisposalRecordCreateReqVO createReqVO);

    PageResult<DisposalRecordDO> getDisposalRecordPage(DisposalRecordPageReqVO pageReqVO);

    DisposalRecordDO getDisposalRecord(Long id);

    void deleteDisposalRecord(Long id);
}
