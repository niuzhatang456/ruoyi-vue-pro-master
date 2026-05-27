package cn.iocoder.yudao.module.jijian.dal.dataobject.Property;

import lombok.*;
import java.util.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import com.baomidou.mybatisplus.annotation.*;
import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;

/**
 * 房产情况 DO
 *
 * @author admin
 */
@TableName("jijian_property")
@KeySequence("jijian_property_seq") // 用于 Oracle、PostgreSQL、Kingbase、DB2、H2 数据库的主键自增。如果是 MySQL 等数据库，可不写。
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PropertyDO extends BaseDO {

    /**
     * ä¸»é”®ç¼–å·
     */
    @TableId
    private Long id;
    /**
     * æˆ¿äº§åœ°å€
     */
    private String propertyAddress;
    /**
     * æˆ¿äº§åç§°
     */
    private String propertyName;
    /**
     * äº§æƒä¿¡æ¯
     */
    private String ownershipInfo;
    /**
     * å»ºç­‘æ—¶é—´
     */
    private LocalDateTime buildingTime;
    /**
     * å»ºç­‘é¢ç§¯ï¼ˆå¹³æ–¹ç±³ï¼‰
     */
    private BigDecimal area;
    /**
     * ç§Ÿèµæƒ…å†µ
     */
    private String leaseStatus;
    /**
     * å¤‡æ³¨
     */
    private String remark;


}