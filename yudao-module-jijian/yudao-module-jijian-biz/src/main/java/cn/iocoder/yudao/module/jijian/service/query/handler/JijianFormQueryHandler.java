package cn.iocoder.yudao.module.jijian.service.query.handler;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianAttendancePageRespVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryDepartmentVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;

import java.util.List;

/**
 * 查询能力抽象 — 表单查询 Handler 接口。
 *
 * <p>每种数据类型（formType）对应一个实现。
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>所有 Mapper 调用必须使用固定白名单方法，不接受动态 SQL。</li>
 *   <li>AI 意图解析结果（JijianAiQueryIntent）必须经过白名单校验后才能传入。</li>
 * </ul>
 *
 * <h3>分页查询两条路径</h3>
 * <ul>
 *   <li>{@link #pageQuery} — 向后兼容的 ATTENDANCE 专用接口，其他 Handler 无需实现。</li>
 *   <li>{@link #genericPageQuery} — 通用接口，所有支持查询的 Handler 必须实现。</li>
 * </ul>
 */
public interface JijianFormQueryHandler {

    /** 本 Handler 负责的数据类型。 */
    JijianQueryFormTypeEnum getFormType();

    /** 是否支持指定 formType。 */
    default boolean supports(String formType) {
        return getFormType().getValue().equals(formType);
    }

    /**
     * 当前 Handler 是否已完整实现（true = 可查询；false = 仅占位，不可查询）。
     * Controller 层根据此值决定是否执行查询，避免 unsupported 类型产生系统异常。
     */
    boolean isSupported();

    /**
     * 获取部门下拉列表。
     * <ul>
     *   <li>有部门字段的 Handler（ATTENDANCE）返回数据库 distinct 部门列表，首项"全单位"。</li>
     * </ul>
     */
    List<JijianQueryDepartmentVO> getDepartments();

    /**
     * 向后兼容的 ATTENDANCE 专用分页查询。
     * 默认实现抛出 UnsupportedOperationException；
     * 仅 AttendanceQueryHandler 需要重写。
     */
    default JijianAttendancePageRespVO pageQuery(JijianAttendancePageReqVO req) {
        throw new UnsupportedOperationException(
                getFormType().getValue() + " does not support attendance-specific pageQuery");
    }

    /**
     * 通用分页查询，所有 supported=true 的 Handler 必须实现。
     * 返回泛化的列表（每行为 Map）+列定义+摘要，前端动态渲染。
     */
    JijianQueryPageRespVO genericPageQuery(JijianQueryPageReqVO req);

    /**
     * 根据 AI 受控意图执行聚合统计查询。
     * 意图必须已通过白名单校验；Mapper 仍使用固定方法。
     *
     * <p>返回类型为 Object，实际是各 Handler 的 SummaryDTO。
     * Jackson 序列化时按实际类型输出字段，前端按 formType 解析。
     */
    Object summaryByIntent(JijianAiQueryIntent intent);
}
