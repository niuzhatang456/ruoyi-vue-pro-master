package cn.iocoder.yudao.module.jijian.service.query;

import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.JijianQueryPageRespVO;
import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import cn.iocoder.yudao.module.jijian.service.query.dto.PropertySummaryDTO;

import java.util.Map;

/**
 * P4 — 房产情况查询 Service。
 *
 * <p>注意：jijian_property 表无 department 字段，故：
 * <ul>
 *   <li>无部门下拉（调用方返回"全单位"即可）。</li>
 *   <li>intent/req 中的 department 字段忽略。</li>
 *   <li>时间范围以 create_time 过滤。</li>
 * </ul>
 */
public interface JijianPropertyQueryService {

    /**
     * 按时间范围分页查询房产记录，返回通用 VO（含列定义）。
     * department 字段忽略（表中无此字段）。
     */
    JijianQueryPageRespVO queryPage(JijianQueryPageReqVO req);

    /**
     * 按 AI 意图返回房产聚合统计。
     * 仅使用 intent.timeRange；intent.department 忽略。
     */
    PropertySummaryDTO querySummary(JijianAiQueryIntent intent);

    /**
     * Returns an aggregate-only numeric matrix for AI summary generation.
     * No detailed rows, table names, schema, or SQL fragments are exposed.
     */
    Map<String, Object> queryMatrix(JijianAiQueryIntent intent);
}
