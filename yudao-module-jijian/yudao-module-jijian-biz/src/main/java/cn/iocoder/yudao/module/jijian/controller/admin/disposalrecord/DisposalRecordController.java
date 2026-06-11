package cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordCreateReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordPageReqVO;
import cn.iocoder.yudao.module.jijian.controller.admin.disposalrecord.vo.DisposalRecordRespVO;
import cn.iocoder.yudao.module.jijian.dal.dataobject.disposalrecord.DisposalRecordDO;
import cn.iocoder.yudao.module.jijian.service.disposalrecord.DisposalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 纪检处置记录")
@RestController
@RequestMapping("/jijian/disposal-record")
@Validated
public class DisposalRecordController {

    @Resource
    private DisposalRecordService disposalRecordService;

    @PostMapping("/create")
    @Operation(summary = "创建处置记录")
    @PreAuthorize("@ss.hasPermission('jijian:disposal:create')")
    public CommonResult<Long> createDisposalRecord(
            @Valid @RequestBody DisposalRecordCreateReqVO createReqVO) {
        return success(disposalRecordService.createDisposalRecord(createReqVO));
    }

    @GetMapping("/page")
    @Operation(summary = "查询本人处置记录分页")
    @PreAuthorize("@ss.hasPermission('jijian:disposal:query')")
    public CommonResult<PageResult<DisposalRecordRespVO>> getDisposalRecordPage(
            @Valid DisposalRecordPageReqVO pageReqVO) {
        PageResult<DisposalRecordDO> pageResult = disposalRecordService.getDisposalRecordPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, DisposalRecordRespVO.class));
    }

    @GetMapping("/get")
    @Operation(summary = "获取本人处置记录详情")
    @PreAuthorize("@ss.hasPermission('jijian:disposal:query')")
    public CommonResult<DisposalRecordRespVO> getDisposalRecord(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(disposalRecordService.getDisposalRecord(id), DisposalRecordRespVO.class));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除本人处置记录")
    @PreAuthorize("@ss.hasPermission('jijian:disposal:delete')")
    public CommonResult<Boolean> deleteDisposalRecord(@RequestParam("id") Long id) {
        disposalRecordService.deleteDisposalRecord(id);
        return success(true);
    }
}
