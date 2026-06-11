package cn.iocoder.yudao.module.jijian.controller.admin.queryhistory;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo.QueryHistoryPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.queryhistory.vo.QueryHistoryRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.queryhistory.QueryHistoryDO;
import cn.iocoder.yudao.module.jijian.service.queryhistory.QueryHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 纪检查询历史")
@RestController
@RequestMapping("/jijian/query-history")
@Validated
public class QueryHistoryController {

    @Resource
    private QueryHistoryService queryHistoryService;

    @GetMapping("/page")
    @Operation(summary = "查询本人历史查询分页")
    @PreAuthorize("@ss.hasPermission('jijian:query-history:query')")
    public CommonResult<PageResult<QueryHistoryRespVO>> getQueryHistoryPage(
            @Valid QueryHistoryPageReqVO pageReqVO) {
        PageResult<QueryHistoryDO> pageResult = queryHistoryService.getQueryHistoryPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, QueryHistoryRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获取本人历史查询详情")
    @PreAuthorize("@ss.hasPermission('jijian:query-history:query')")
    public CommonResult<QueryHistoryRespVO> getQueryHistory(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(queryHistoryService.getQueryHistory(id), QueryHistoryRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除本人历史查询")
    @PreAuthorize("@ss.hasPermission('jijian:query-history:delete')")
    public CommonResult<Boolean> deleteQueryHistory(@RequestParam("id") Long id) {
        queryHistoryService.deleteQueryHistory(id);
        return success(true);
    }
}
