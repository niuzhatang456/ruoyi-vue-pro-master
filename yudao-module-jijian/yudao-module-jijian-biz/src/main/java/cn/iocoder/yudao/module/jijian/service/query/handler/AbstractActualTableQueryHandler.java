package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.JijianActualTableQueryService;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

import javax.annotation.Resource;
import java.util.List;

public abstract class AbstractActualTableQueryHandler implements JijianFormQueryHandler {

    @Resource
    private JijianActualTableQueryService actualTableQueryService;

    protected abstract JijianQueryFormTypeEnum actualFormType();

    @Override
    public JijianQueryFormTypeEnum getFormType() {
        return actualFormType();
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    @Override
    public List<JijianQueryDepartmentVO> getDepartments() {
        return actualTableQueryService.listDepartments(actualFormType());
    }

    @Override
    public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
        return actualTableQueryService.queryPage(actualFormType(), req);
    }

    @Override
    public Object summaryByIntent(JijianAiQueryIntent intent) {
        return actualTableQueryService.querySummary(actualFormType(), intent);
    }
}
