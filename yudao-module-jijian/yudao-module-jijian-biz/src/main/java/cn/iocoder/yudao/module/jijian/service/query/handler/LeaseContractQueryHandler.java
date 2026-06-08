package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class LeaseContractQueryHandler extends AbstractActualTableQueryHandler {
    @Override
    protected JijianQueryFormTypeEnum actualFormType() {
        return JijianQueryFormTypeEnum.LEASE_CONTRACT;
    }
}
