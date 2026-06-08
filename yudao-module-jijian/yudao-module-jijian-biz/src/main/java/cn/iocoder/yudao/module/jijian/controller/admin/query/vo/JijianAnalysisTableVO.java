package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Schema(description = "纪检查询 - 分析明细表 VO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JijianAnalysisTableVO {

    @Schema(description = "表格标题")
    private String title;

    @Schema(description = "列定义，每项包含 key 和 label")
    private List<Map<String, String>> columns;

    @Schema(description = "行数据")
    private List<Map<String, Object>> rows;
}
