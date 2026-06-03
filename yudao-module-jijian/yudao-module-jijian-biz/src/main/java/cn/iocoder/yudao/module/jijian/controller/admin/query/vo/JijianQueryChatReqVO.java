package cn.iocoder.yudao.module.jijian.controller.admin.query.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 纪检受控 AI 查询请求 VO")
@Data
public class JijianQueryChatReqVO {

    @Schema(description = "会话 ID，可为空，首次请求不传", example = "conv-001")
    private String conversationId;

    @Schema(description = "数据类型默认值（前端传入，AI 无法识别时回退）", example = "ATTENDANCE")
    private String formType = "ATTENDANCE";

    @Schema(description = "部门默认值", example = "ALL")
    private String department = "ALL";

    @Schema(description = "时间范围默认值", example = "ONE_WEEK")
    private String timeRange = "ONE_WEEK";

    @Schema(description = "自然语言问题", required = true,
            example = "我需要知道一周内全单位的调休信息，有多少人出勤，多少人调休，各部门分别多少人")
    private String message;

    @Schema(description = "历史对话，最多保留近 6 轮")
    private List<ChatHistoryItem> history;

    @Data
    public static class ChatHistoryItem {
        @Schema(description = "角色：user / assistant")
        private String role;
        @Schema(description = "内容")
        private String content;
    }
}
