package cn.iocoder.yudao.module.jijian.controller.admin.parseddata.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 通用确认写入 Response VO")
@Data
public class ConfirmWriteResultRespVO {

    @Schema(description = "解析记录 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long parsedDataId;

    @Schema(description = "业务类型", example = "房产信息")
    private String formType;

    @Schema(description = "写入的业务记录 ID 列表")
    private List<Long> confirmedIds;

    @Schema(description = "写入数量")
    private int confirmedCount;

    @Schema(description = "是否幂等（重复确认）")
    private boolean idempotent;

    @Schema(description = "写入的正式业务表名", example = "jijian_canteen_supplier")
    private String businessTable;

}
