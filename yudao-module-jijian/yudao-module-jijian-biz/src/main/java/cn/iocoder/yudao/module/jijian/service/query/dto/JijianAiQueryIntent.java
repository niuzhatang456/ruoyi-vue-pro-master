package cn.iocoder.yudao.module.jijian.service.query.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 受控查询意图 DTO
 *
 * 只允许枚举值，不允许携带 SQL / 表名 / 字段名 / where 字符串。
 * 后端对每个字段做白名单校验。
 */
@Data
public class JijianAiQueryIntent {

    /** 数据类型，对应 JijianQueryFormTypeEnum.value，白名单校验 */
    private String formType;

    /** 部门，"ALL" 表示全单位，其余须来自 distinct 查询结果 */
    private String department;

    /** 时间范围，对应 JijianQueryTimeRangeEnum.value，白名单校验 */
    private String timeRange;

    /** 统计指标列表，对应 JijianQueryMetricEnum.value，白名单校验 */
    private List<String> metrics;

    /** 是否需要明细样本 */
    private boolean needDetail;

    /** 分析目标，仅用于回答边界判断，不参与 SQL 或动态查询 */
    private String analysisGoal;

    /** 下钻目标，仅用于本地 handler 允许范围内的解释 */
    private String drillDownTarget;

    /** AI 解析置信度，仅用于展示和调试 */
    private Double confidence;

    /** 用户原始消息，仅用于跨表检查和边界判断，不参与查询 */
    private String originalMessage;

    /** 目标人员姓名，用于个人明细查询，来自 AI 解析或本地关键词提取 */
    private String personName;
}
