package cn.iocoder.yudao.module.jijian.dal.dataobject.canteenmarketprice;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/** 纪检民生商品市场零售价格公告 DO */
@TableName("jijian_canteen_market_price")
@KeySequence("jijian_canteen_market_price_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CanteenMarketPriceDO extends TenantBaseDO {
    @TableId
    private Long id;
    private String itemName;
    private String specLevel;
    private String unit;
    private BigDecimal price;
    private String pricePoint;
    private String priceMonth;
    private String sourceTitle;
    private String sourceFileName;
    private Long sourceParsedDataId;
}
