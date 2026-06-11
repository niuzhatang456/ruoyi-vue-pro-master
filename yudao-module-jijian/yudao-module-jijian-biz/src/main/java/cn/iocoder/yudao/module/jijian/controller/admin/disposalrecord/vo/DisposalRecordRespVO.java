package cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DisposalRecordRespVO {

    private Long id;
    private Long queryHistoryId;
    private String queryQuestion;
    private String queryAnswer;
    private String queryResultJson;
    private String disposalOpinion;
    private Long disposalUserId;
    private String disposalUserName;
    private LocalDateTime disposalTime;
    private String sourceModule;
    private String remark;
    private LocalDateTime createTime;
}
