package cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 纪检处置记录分页请求")
@Data
@EqualsAndHashCode(callSuper = true)
public class DisposalRecordPageReqVO extends PageParam {

    private String queryQuestion;
}
