package cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 纪检查询历史响应")
@Data
public class QueryHistoryRespVO {

    private Long id;
    private Long userId;
    private String userName;
    private String question;
    private String answer;
    private String queryType;
    private String formType;
    private String querySql;
    private String queryResultJson;
    private String databaseContextMetaJson;
    private String modelName;
    private Boolean success;
    private String errorMessage;
    private String remark;
    private LocalDateTime createTime;
}
