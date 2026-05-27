package cn.iocoder.yudao.module.system.controller.admin.ai;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AiQueryAttendanceReqVO;
import cn.iocoder.yudao.module.system.controller.admin.ai.vo.AiQueryAttendanceRespVO;
import cn.iocoder.yudao.module.system.service.ai.AiQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 智能查询")
@RestController
@RequestMapping("/system/ai-query")
@Validated
public class AiQueryController {

    @Resource
    private AiQueryService aiQueryService;

    @PostMapping("/attendance")
    @Operation(summary = "智能查询考勤日报表", description = "接收自然语言，规则解析后查询 attendance_daily_report 表")
    @PreAuthorize("@ss.hasPermission('system:ai-query:attendance')")
    public CommonResult<AiQueryAttendanceRespVO> queryAttendance(@Valid @RequestBody AiQueryAttendanceReqVO reqVO) {
        return success(aiQueryService.queryAttendance(reqVO.getMessage()));
    }

}
