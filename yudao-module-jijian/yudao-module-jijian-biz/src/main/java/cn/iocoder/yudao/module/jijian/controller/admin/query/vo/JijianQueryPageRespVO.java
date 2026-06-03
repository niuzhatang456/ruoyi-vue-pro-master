package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 通用分页查询响应 VO。
 *
 * <p>每行数据以 Map&lt;String, Object&gt; 返回，前端根据 columns 定义动态渲染列。
 * summary 字段为各 Handler 自定义的聚合结果。
 */
@Schema(description = "管理后台 - 纪检通用分页查询 Response VO")
@Data
public class JijianQueryPageRespVO {

    @Schema(description = "分页数据（每行为字段名 -> 值的映射）")
    private PageResult<Map<String, Object>> pageResult;

    @Schema(description = "聚合统计结果（具体结构依 formType 而定）")
    private Object summary;

    @Schema(description = "列定义（用于前端动态渲染表头）")
    private List<ColumnDef> columns;

    /** 列定义：key=字段名，label=列头文字，type=渲染类型（text/number/date） */
    @Data
    public static class ColumnDef {
        private String key;
        private String label;
        private String type; // "text" | "number" | "date"

        public static ColumnDef of(String key, String label, String type) {
            ColumnDef c = new ColumnDef();
            c.key = key;
            c.label = label;
            c.type = type;
            return c;
        }
    }
}
