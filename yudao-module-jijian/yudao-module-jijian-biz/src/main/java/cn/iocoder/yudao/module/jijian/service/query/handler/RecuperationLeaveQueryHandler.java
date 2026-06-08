package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import org.springframework.stereotype.Component;

@Component
public class RecuperationLeaveQueryHandler extends AbstractActualTableQueryHandler {
    @Override
    protected JijianQueryFormTypeEnum actualFormType() {
        return JijianQueryFormTypeEnum.RECUPERATION_LEAVE;
    }
}
