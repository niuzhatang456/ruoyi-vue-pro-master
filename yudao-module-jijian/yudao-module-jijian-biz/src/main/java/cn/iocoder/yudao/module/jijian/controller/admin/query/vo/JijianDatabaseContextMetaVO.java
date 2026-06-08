package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "纪检查询 - 数据库数据包元信息 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JijianDatabaseContextMetaVO {

    @Schema(description = "使用的数据表名列表")
    private List<String> tablesUsed;

    @Schema(description = "各表行数")
    private Map<String, Integer> rowCounts;

    @Schema(description = "数据来源：database / mock")
    private String dataSource;

    @Schema(description = "敏感字段是否已移除")
    private boolean sensitiveFieldsRemoved;

    @Schema(description = "数据是否被截断")
    private boolean truncated;

    @Schema(description = "分析时间范围描述")
    private String timeRange;
}
