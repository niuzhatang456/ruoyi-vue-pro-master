package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 纪检受控 AI 查询响应 VO")
@Data
public class JijianQueryChatRespVO {

    @Schema(description = "会话 ID")
    private String conversationId;

    @Schema(description = "AI 生成的中文总结")
    private String answer;

    @Schema(description = "最终查询的数据类型")
    private String formType;

    @Schema(description = "解析出的受控查询意图（已经过白名单校验）")
    private JijianAiQueryIntent queryIntent;

    @Schema(description = "基于意图查询数据库后的聚合统计结果（具体结构依 formType 而定）")
    private Object data;

    @Schema(description = "聚合统计结果（新结构，等同 data，前端优先读取该字段）")
    private Object summary = Collections.emptyMap();

    @Schema(description = "列定义（用于前端动态渲染表头）")
    private List<JijianQueryPageRespVO.ColumnDef> columns = Collections.emptyList();

    @Schema(description = "分页数据（每行为字段名 -> 值的映射）")
    private PageResult<Map<String, Object>> pageResult = new PageResult<>(Collections.emptyList(), 0L);

    @Schema(description = "AI 模式：LOCAL_FALLBACK / DEEPSEEK_INTENT / DEEPSEEK_SUMMARY / DEEPSEEK_DATA_ANALYSIS")
    private String aiMode;

    // ===== 新增：图表化分析结果字段 =====

    @Schema(description = "指标卡片列表")
    private List<JijianMetricVO> metrics = Collections.emptyList();

    @Schema(description = "图表列表（pie/bar/line）")
    private List<JijianChartVO> charts = Collections.emptyList();

    @Schema(description = "明细分析表格")
    private List<JijianAnalysisTableVO> tables = Collections.emptyList();

    @Schema(description = "数据库数据包元信息")
    private JijianDatabaseContextMetaVO databaseContextMeta;
}
