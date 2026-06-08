package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.CanteenSupplySummaryDTO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

import java.util.Map;

public interface CanteenSupplyQueryService {

    JijianQueryPageRespVO queryPage(JijianQueryPageReqVO req);

    CanteenSupplySummaryDTO querySummary(JijianAiQueryIntent intent);

    Map<String, Object> queryMatrix(JijianAiQueryIntent intent);
}
