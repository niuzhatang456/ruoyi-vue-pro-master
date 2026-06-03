package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import cn.iocoder.yudao.module.jijian.service.query.dto.JijianAiQueryIntent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - 纪检受控 AI 查询响应 VO")
@Data
public class JijianQueryChatRespVO {

    @Schema(description = "会话 ID")
    private String conversationId;

    @Schema(description = "AI 生成的中文总结")
    private String answer;

    @Schema(description = "解析出的受控查询意图（已经过白名单校验）")
    private JijianAiQueryIntent queryIntent;

    @Schema(description = "基于意图查询数据库后的聚合统计结果（具体结构依 formType 而定）")
    private Object data;

    @Schema(description = "AI 模式：LOCAL_FALLBACK / DEEPSEEK_INTENT / DEEPSEEK_SUMMARY")
    private String aiMode;
}
