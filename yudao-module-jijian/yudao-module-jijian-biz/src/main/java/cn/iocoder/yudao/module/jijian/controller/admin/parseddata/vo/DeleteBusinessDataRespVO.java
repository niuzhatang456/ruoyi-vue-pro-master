package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "删除导入批次业务数据 - 响应 VO")
@Data
public class DeleteBusinessDataRespVO {

    @Schema(description = "解析数据 ID（批次标识）")
    private Long parsedDataId;

    @Schema(description = "业务表名")
    private String businessTable;

    @Schema(description = "实际删除行数")
    private int deletedRows;

    @Schema(description = "操作结果描述")
    private String message;

}
