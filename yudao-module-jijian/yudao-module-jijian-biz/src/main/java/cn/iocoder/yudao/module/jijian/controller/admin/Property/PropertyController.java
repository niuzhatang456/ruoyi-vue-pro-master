package cn.iocoder.yudao.module.jijian.controller.admin.Property;

import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;

import javax.validation.constraints.*;
import javax.validation.*;
import javax.servlet.http.*;
import java.util.*;
import java.io.IOException;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

import cn.iocoder.yudao.framework.excel.core.util.ExcelUtils;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import static cn.iocoder.yudao.framework.apilog.core.enums.OperateTypeEnum.*;

import cn.iocoder.yudao.module.jijian.controller.admin.Property.vo.*;
import cn.iocoder.yudao.module.jijian.dal.dataobject.Property.PropertyDO;
import cn.iocoder.yudao.module.jijian.service.Property.PropertyService;

@Tag(name = "管理后台 - 房产情况")
@RestController
@RequestMapping("/jijian/property")
@Validated
public class PropertyController {

    @Resource
    private PropertyService propertyService;

    @PostMapping("/create")
    @Operation(summary = "创建房产情况")
    @PreAuthorize("@ss.hasPermission('jijian:property:create')")
    public CommonResult<Long> createProperty(@Valid @RequestBody PropertySaveReqVO createReqVO) {
        return success(propertyService.createProperty(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新房产情况")
    @PreAuthorize("@ss.hasPermission('jijian:property:update')")
    public CommonResult<Boolean> updateProperty(@Valid @RequestBody PropertySaveReqVO updateReqVO) {
        propertyService.updateProperty(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除房产情况")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('jijian:property:delete')")
    public CommonResult<Boolean> deleteProperty(@RequestParam("id") Long id) {
        propertyService.deleteProperty(id);
        return success(true);
    }

    @DeleteMapping("/delete-list")
    @Parameter(name = "ids", description = "编号", required = true)
    @Operation(summary = "批量删除房产情况")
                @PreAuthorize("@ss.hasPermission('jijian:property:delete')")
    public CommonResult<Boolean> deletePropertyList(@RequestParam("ids") List<Long> ids) {
        propertyService.deletePropertyListByIds(ids);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得房产情况")
    @Parameter(name = "id", description = "编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('jijian:property:query')")
    public CommonResult<PropertyRespVO> getProperty(@RequestParam("id") Long id) {
        PropertyDO property = propertyService.getProperty(id);
        return success(BeanUtils.toBean(property, PropertyRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得房产情况分页")
    @PreAuthorize("@ss.hasPermission('jijian:property:query')")
    public CommonResult<PageResult<PropertyRespVO>> getPropertyPage(@Valid PropertyPageReqVO pageReqVO) {
        PageResult<PropertyDO> pageResult = propertyService.getPropertyPage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, PropertyRespVO.class));
    }

    @GetMapping("/export-excel")
    @Operation(summary = "导出房产情况 Excel")
    @PreAuthorize("@ss.hasPermission('jijian:property:export')")
    @ApiAccessLog(operateType = EXPORT)
    public void exportPropertyExcel(@Valid PropertyPageReqVO pageReqVO,
              HttpServletResponse response) throws IOException {
        pageReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<PropertyDO> list = propertyService.getPropertyPage(pageReqVO).getList();
        // 导出 Excel
        ExcelUtils.write(response, "房产情况.xls", "数据", PropertyRespVO.class,
                        BeanUtils.toBean(list, PropertyRespVO.class));
    }

}