package cn.iocoder.yudao.module.jijian.service.queryhistory;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryChatRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo.QueryHistoryPageReqVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory.QueryHistoryDO;

public interface QueryHistoryService {

    Long createQueryHistory(JijianQueryChatReqVO reqVO, JijianQueryChatRespVO respVO,
                            String queryType, boolean success, String errorMessage);

    PageResult<QueryHistoryDO> getQueryHistoryPage(QueryHistoryPageReqVO pageReqVO);

    QueryHistoryDO getQueryHistory(Long id);

    void deleteQueryHistory(Long id);
}
