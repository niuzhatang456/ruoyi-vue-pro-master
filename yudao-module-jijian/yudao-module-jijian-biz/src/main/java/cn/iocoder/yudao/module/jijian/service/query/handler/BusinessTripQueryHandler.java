package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class BusinessTripQueryHandler extends AbstractActualTableQueryHandler {
    @Override
    protected JijianQueryFormTypeEnum actualFormType() {
        return JijianQueryFormTypeEnum.BUSINESS_TRIP;
    }
}
