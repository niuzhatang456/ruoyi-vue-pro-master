package cn.iocoder.yudao.module.jijian.controller.admin.Property.vo;

import lombok.*;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;
import cn.iocoder.yudao.framework.common.pojo.PageParam;
import java.math.BigDecimal;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 房产情况分页 Request VO")
@Data
public class PropertyPageReqVO extends PageParam {

    @Schema(description = "æˆ¿äº§åœ°å€")
    private String propertyAddress;

    @Schema(description = "æˆ¿äº§åç§°", example = "芋艿")
    private String propertyName;

    @Schema(description = "äº§æƒä¿¡æ¯")
    private String ownershipInfo;

    @Schema(description = "å»ºç­‘æ—¶é—´")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] buildingTime;

    @Schema(description = "å»ºç­‘é¢ç§¯ï¼ˆå¹³æ–¹ç±³ï¼‰")
    private BigDecimal area;

    @Schema(description = "ç§Ÿèµæƒ…å†µ", example = "1")
    private String leaseStatus;

    @Schema(description = "å¤‡æ³¨", example = "你猜")
    private String remark;

    @Schema(description = "åˆ›å»ºæ—¶é—´")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}