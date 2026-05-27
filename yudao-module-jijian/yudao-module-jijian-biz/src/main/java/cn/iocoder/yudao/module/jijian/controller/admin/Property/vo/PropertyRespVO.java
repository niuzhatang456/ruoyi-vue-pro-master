package cn.iocoder.yudao.module.jijian.controller.admin.Property.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 房产情况 Response VO")
@Data
public class PropertyRespVO {

    @Schema(description = "主键编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @ExcelProperty("主键编号")
    private Long id;

    @Schema(description = "房产地址", example = "朝阳路1号")
    @ExcelProperty("房产地址")
    private String propertyAddress;

    @Schema(description = "房产名称", example = "办公楼A座")
    @ExcelProperty("房产名称")
    private String propertyName;

    @Schema(description = "产权信息", example = "单位自有")
    @ExcelProperty("产权信息")
    private String ownershipInfo;

    @Schema(description = "建筑时间")
    @ExcelProperty("建筑时间")
    private LocalDateTime buildingTime;

    @Schema(description = "建筑面积（平方米）", example = "100.50")
    @ExcelProperty("建筑面积")
    private BigDecimal area;

    @Schema(description = "租赁情况", example = "未出租")
    @ExcelProperty("租赁情况")
    private String leaseStatus;

    @Schema(description = "备注", example = "测试数据")
    @ExcelProperty("备注")
    private String remark;

    @Schema(description = "创建时间")
    @ExcelProperty("创建时间")
    private LocalDateTime createTime;

}