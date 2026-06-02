package cn.iocoder.yudao.module.jijian.dal.dataobject.Property;

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
import java.time.LocalDateTime;

/**
 * 房产情况 DO
 */
@TableName("jijian_property")
@KeySequence("jijian_property_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDO extends TenantBaseDO {

    /** 主键编号 */
    @TableId
    private Long id;

    /** 房产地址 */
    private String propertyAddress;

    /** 房产名称 */
    private String propertyName;

    /** 产权信息 */
    private String ownershipInfo;

    /** 建筑时间 */
    private LocalDateTime buildingTime;

    /** 建筑面积（平方米） */
    private BigDecimal area;

    /** 租赁情况 */
    private String leaseStatus;

    /** 备注 */
    private String remark;

    /** 来源解析数据ID，关联 jijian_import_parsed_data.id，可追溯导入记录 */
    private Long sourceParsedDataId;

}
