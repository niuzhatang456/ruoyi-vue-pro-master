package cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Schema(description = "管理后台 - 创建纪检处置记录请求")
@Data
public class DisposalRecordCreateReqVO {

    private Long queryHistoryId;
    private String queryQuestion;
    private String queryAnswer;
    private String queryResultJson;

    @NotBlank(message = "处置意见不能为空")
    private String disposalOpinion;

    private String sourceModule;
    private String remark;
}
