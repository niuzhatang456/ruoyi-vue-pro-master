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
}
