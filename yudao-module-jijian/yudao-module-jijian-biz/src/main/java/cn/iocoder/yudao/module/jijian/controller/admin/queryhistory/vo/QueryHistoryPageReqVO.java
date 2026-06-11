package cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 纪检查询历史分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class QueryHistoryPageReqVO extends PageParam {

    @Schema(description = "问题关键字")
    private String question;
}
