package cn.iocoder.yudao.module.jijian.dal.dataobject.canteensupplier;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 纪检食堂供应商信息 DO */
@TableName("jijian_canteen_supplier")
@KeySequence("jijian_canteen_supplier_seq")
@Data @EqualsAndHashCode(callSuper = true) @ToString(callSuper = true)
@Builder @NoArgsConstructor @AllArgsConstructor
public class CanteenSupplierDO extends TenantBaseDO {
    @TableId private Long id;
    private String itemName;
    private String specLevel;
    private String unit;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal subtotal;
    private String supplierName;
    private LocalDate supplyDate;
    private String purchasePoint;
    private String remark;
    private Long sourceParsedDataId;
}
