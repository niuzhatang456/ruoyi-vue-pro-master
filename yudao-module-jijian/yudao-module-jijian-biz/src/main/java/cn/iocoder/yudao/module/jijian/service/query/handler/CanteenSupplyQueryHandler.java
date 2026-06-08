package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.CanteenSupplyQueryService;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Component
public class CanteenSupplyQueryHandler implements JijianFormQueryHandler {

    @Resource
    private CanteenSupplyQueryService canteenSupplyQueryService;

    @Override
    public JijianQueryFormTypeEnum getFormType() {
        return JijianQueryFormTypeEnum.CANTEEN_SUPPLY;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public List<JijianQueryDepartmentVO> getDepartments() {
        return List.of(new JijianQueryDepartmentVO("ALL", "全单位"));
    }

    @Override
    public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
        return canteenSupplyQueryService.queryPage(req);
    }

    @Override
    public Object summaryByIntent(JijianAiQueryIntent intent) {
        return canteenSupplyQueryService.queryMatrix(intent);
    }
}
