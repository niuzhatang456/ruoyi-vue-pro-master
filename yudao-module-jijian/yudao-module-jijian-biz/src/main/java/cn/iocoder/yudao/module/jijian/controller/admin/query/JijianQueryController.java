package cn.iocoder.yudao.module.jijian.controller.admin.query;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.jijian.controller.admin.query.vo.*;
import cn.iocoder.yudao.module.jijian.enums.query.JijianQueryFormTypeEnum;
import cn.iocoder.yudao.module.jijian.service.query.JijianActualTableQueryService;
import cn.iocoder.yudao.module.jijian.service.query.JijianQueryChatService;
import cn.iocoder.yudao.module.jijian.service.query.handler.JijianFormQueryHandler;
import cn.iocoder.yudao.module.jijian.service.query.handler.JijianFormQueryHandlerRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 纪检查询模块 P3")
@RestController
@RequestMapping("/jijian/query")
@Validated
@Slf4j
public class JijianQueryController {

    @Resource
    private JijianFormQueryHandlerRegistry handlerRegistry;

    @Resource
    private JijianQueryChatService queryChatService;

    @Resource
    private JijianActualTableQueryService actualTableQueryService;

    // ------------------------------------------------------------------ //
    // 一、数据类型下拉接口
    // ------------------------------------------------------------------ //

    @GetMapping("/form-types")
    @Operation(summary = "获取支持查询的数据类型列表（全部枚举，unsupported 类型置灰）")
    public CommonResult<List<JijianQueryFormTypeVO>> listFormTypes() {
        List<JijianQueryFormTypeVO> result = Arrays.stream(JijianQueryFormTypeEnum.values())
                .filter(JijianQueryFormTypeEnum::isPrimary)
                .map(e -> new JijianQueryFormTypeVO(e.getValue(), e.getLabel(), e.isSupported()))
                .collect(Collectors.toList());
        return success(result);
    }

    // ------------------------------------------------------------------ //
    // 二、部门下拉接口 — 通过 Registry 按 formType 路由
    // ------------------------------------------------------------------ //

    @GetMapping("/departments")
    @Operation(summary = "获取部门列表（按数据类型路由，来自真实数据库 distinct 值）")
    @Parameter(name = "formType", description = "数据类型", example = "ATTENDANCE_DAILY")
    public CommonResult<List<JijianQueryDepartmentVO>> listDepartments(
            @RequestParam(value = "formType", defaultValue = "ATTENDANCE_DAILY") String formType) {

        JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(formType);
        if (handler == null || !handler.isSupported()) {
            return success(List.of(new JijianQueryDepartmentVO("ALL", "全部（该类型暂不支持查询）")));
        }
        return success(handler.getDepartments());
    }

    @GetMapping("/filter-options")
    @Operation(summary = "获取真实过滤选项（部门 / 月份 / 日期范围，均来自数据库，不含 mock 数据）")
    @Parameter(name = "type", description = "数据类型", example = "ATTENDANCE_DAILY")
    public CommonResult<JijianQueryFilterOptionsVO> getFilterOptions(
            @RequestParam(value = "type", defaultValue = "ATTENDANCE_DAILY") String type) {

        JijianQueryFormTypeEnum formType = JijianQueryFormTypeEnum.of(type);
        if (formType == null || !formType.isSupported()) {
            return success(JijianQueryFilterOptionsVO.builder()
                    .departments(Collections.emptyList())
                    .months(Collections.emptyList())
                    .dateRange(JijianQueryFilterOptionsVO.DateRange.builder().build())
                    .hasDepartment(false)
                    .hasDateField(false)
                    .build());
        }
        return success(actualTableQueryService.getFilterOptions(formType));
    }

    // ------------------------------------------------------------------ //
    // 三a. 向后兼容 — 考勤专用分页查询（保留旧路径，前端已有调用）
    // ------------------------------------------------------------------ //

    @PostMapping("/attendance/page")
    @Operation(summary = "考勤信息分页查询（向后兼容；新前端请使用 /query/page）")
    public CommonResult<JijianAttendancePageRespVO> queryAttendancePage(
            @Valid @RequestBody JijianAttendancePageReqVO req) {

        JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull("ATTENDANCE");
        if (handler == null || !handler.isSupported()) {
            log.error("[QueryController] ATTENDANCE handler not found — check handler registration");
            return success(new JijianAttendancePageRespVO());
        }
        return success(handler.pageQuery(req));
    }

    // ------------------------------------------------------------------ //
    // 三b. 通用分页查询 — 通过 Registry 按 formType 路由
    // ------------------------------------------------------------------ //

    @PostMapping("/page")
    @Operation(summary = "通用分页查询：支持 ATTENDANCE / REAL_ESTATE，按 formType 路由")
    public CommonResult<JijianQueryPageRespVO> queryPage(
            @Valid @RequestBody JijianQueryPageReqVO req) {

        JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(req.getFormType());
        if (handler == null || !handler.isSupported()) {
            log.warn("[QueryController] Unsupported formType={} in /query/page", req.getFormType());
            JijianQueryPageRespVO empty = new JijianQueryPageRespVO();
            empty.setPageResult(new cn.iocoder.yudao.framework.common.pojo.PageResult<>());
            empty.setColumns(List.of());
            return success(empty);
        }
        return success(handler.genericPageQuery(req));
    }

    @GetMapping("/list")
    @Operation(summary = "通用列表查询：支持 ATTENDANCE / REAL_ESTATE，按 type 路由")
    public CommonResult<JijianQueryPageRespVO> queryList(
            @RequestParam(value = "type", defaultValue = "ATTENDANCE") String type,
            @RequestParam(value = "department", defaultValue = "ALL") String department,
            @RequestParam(value = "timeRange", defaultValue = "ONE_MONTH") String timeRange,
            @RequestParam(value = "pageNo", defaultValue = "1") Integer pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        JijianQueryPageReqVO req = new JijianQueryPageReqVO();
        req.setFormType(type);
        req.setDepartment(department);
        req.setTimeRange(timeRange);
        req.setPageNo(pageNo);
        req.setPageSize(pageSize);
        JijianFormQueryHandler handler = handlerRegistry.getHandlerOrNull(req.getFormType());
        if (handler == null || !handler.isSupported()) {
            log.warn("[QueryController] Unsupported formType={} in /query/list", req.getFormType());
            JijianQueryPageRespVO empty = new JijianQueryPageRespVO();
            empty.setPageResult(new cn.iocoder.yudao.framework.common.pojo.PageResult<>());
            empty.setColumns(List.of());
            return success(empty);
        }
        return success(handler.genericPageQuery(req));
    }

    // ------------------------------------------------------------------ //
    // 四、受控 AI 查询接口
    // ------------------------------------------------------------------ //

    @PostMapping("/chat")
    @Operation(summary = "受控 AI 自然语言查询（aiMode 反映实际 AI 参与情况）")
    public CommonResult<JijianQueryChatRespVO> chat(
            @Valid @RequestBody JijianQueryChatReqVO req) {
        return success(queryChatService.chat(req));
    }
}
