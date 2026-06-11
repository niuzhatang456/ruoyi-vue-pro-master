package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "SQL 查询追踪记录")
@Data
public class JijianSqlTraceVO {

    @Schema(description = "查询目的说明")
    private String purpose;

    @Schema(description = "执行的 SQL 语句")
    private String sql;

    @Schema(description = "查询返回的总行数")
    private int rowCount;

    @Schema(description = "执行错误信息（成功时为 null）")
    private String error;

    @Schema(description = "SQL 实际执行时间（格式：yyyy-MM-dd HH:mm:ss）")
    private String executedAt;

    @Schema(description = "数据来源标识（current_database / preResolve / fallback）")
    private String source;
}
