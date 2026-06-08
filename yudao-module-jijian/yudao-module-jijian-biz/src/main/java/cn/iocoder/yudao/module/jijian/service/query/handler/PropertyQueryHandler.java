package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.JijianPropertyQueryService;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

/**
 * P4 查询 Handler — 房产情况（REAL_ESTATE）。
 *
 * <p>注意：jijian_property 表无 department 字段，故：
 * <ul>
 *   <li>{@link #getDepartments()} 只返回"全单位"。</li>
 *   <li>查询时 department 参数忽略。</li>
 *   <li>时间范围以 create_time 过滤。</li>
 * </ul>
 *
 * <p>通过 Spring 注入自动注册到 {@link JijianFormQueryHandlerRegistry}，无需手工注册。
 */
@Component
public class PropertyQueryHandler implements JijianFormQueryHandler {

    @Resource
    private JijianPropertyQueryService propertyQueryService;

    @Override
    public JijianQueryFormTypeEnum getFormType() {
        return JijianQueryFormTypeEnum.REAL_ESTATE;
    }

    @Override
    public boolean isSupported() {
        return true;
    }

    /**
     * 房产表无 department 字段，只返回"全单位"。
     */
    @Override
    public List<JijianQueryDepartmentVO> getDepartments() {
        return List.of(new JijianQueryDepartmentVO("ALL", "全单位（房产表无部门字段）"));
    }

    @Override
    public JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req) {
        return propertyQueryService.queryPage(req);
    }

    @Override
    public Object summaryByIntent(JijianAiQueryIntent intent) {
        return propertyQueryService.queryMatrix(intent);
    }
}
